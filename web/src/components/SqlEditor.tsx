"use client";

import dynamic from "next/dynamic";
import { useRef, useState } from "react";
import { filterCatalogNames, insertCatalogName, sqlClause, type CatalogName } from "@/lib/sql-names";

const Monaco = dynamic(() => import("@monaco-editor/react"), { ssr: false });

type EditorApi = {
  getValue: () => string;
  getPosition: () => { lineNumber: number; column: number } | null;
  getModel: () => { getLineContent: (line: number) => string; getLineCount: () => number } | null;
  executeEdits: (
    source: string,
    edits: { range: { startLineNumber: number; startColumn: number; endLineNumber: number; endColumn: number }; text: string }[],
  ) => void;
  getScrolledVisiblePosition: (pos: { lineNumber: number; column: number }) => { top: number; left: number; height: number } | null;
  onDidChangeCursorPosition: (fn: () => void) => { dispose: () => void };
  onKeyDown: (fn: (event: { keyCode: number; shiftKey: boolean; ctrlKey: boolean; altKey: boolean; metaKey: boolean; preventDefault: () => void; stopPropagation: () => void }) => void) => void;
  focus: () => void;
};

type MonacoApi = {
  KeyCode: { Tab: number; Enter: number; Escape: number; Space: number };
  editor: { defineTheme: (name: string, theme: object) => void; setTheme: (name: string) => void };
};

export type SqlEditorHandle = {
  getValue: () => string;
  insertAtCursor: (text: string) => void;
};

type Props = {
  value?: string;
  defaultValue?: string;
  height?: string;
  names: CatalogName[];
  onChange: (sql: string) => void;
  onReady?: (handle: SqlEditorHandle) => void;
};

type OpenList = {
  items: CatalogName[];
  index: number;
  top: number;
  left: number;
  line: number;
  from: number;
  to: number;
  token: string;
};

type TokenAtCursor = {
  pos: { lineNumber: number; column: number };
  match: string;
  from: number;
  to: number;
};

export function SqlEditor({ value, defaultValue, height = "280px", names, onChange, onReady }: Props) {
  const hostRef = useRef<HTMLDivElement | null>(null);
  const editorRef = useRef<EditorApi | null>(null);
  const namesRef = useRef(names);
  const listRef = useRef<OpenList | null>(null);
  const [list, setList] = useState<OpenList | null>(null);
  namesRef.current = names;

  function setOpenList(next: OpenList | null) {
    listRef.current = next;
    setList(next);
  }

  function closeList() {
    setOpenList(null);
  }

  function textBeforeCursor(editor: EditorApi) {
    const pos = editor.getPosition();
    const model = editor.getModel();
    if (!pos || !model) return "";
    let before = "";
    for (let line = 1; line < pos.lineNumber; line += 1) {
      before += model.getLineContent(line) + "\n";
    }
    return before + model.getLineContent(pos.lineNumber).slice(0, pos.column - 1);
  }

  function tokenAtCursor(editor: EditorApi): TokenAtCursor | null {
    const pos = editor.getPosition();
    const model = editor.getModel();
    if (!pos || !model) return null;
    const line = model.getLineContent(pos.lineNumber);
    const before = line.slice(0, pos.column - 1);
    const after = line.slice(pos.column - 1);
    const prefix = before.match(/@[A-Za-z0-9_]*$|[A-Za-z_][A-Za-z0-9_.]*$/)?.[0] ?? "";
    const suffix = after.match(/^[A-Za-z0-9_.]*/)?.[0] ?? "";
    const match = prefix + suffix;
    const from = pos.column - prefix.length;
    const to = pos.column + suffix.length;
    return { pos, match, from, to };
  }

  function openList() {
    const editor = editorRef.current;
    if (!editor) return;
    const token = tokenAtCursor(editor);
    if (!token) return;
    const items = filterCatalogNames(namesRef.current, token.match, sqlClause(textBeforeCursor(editor)));
    const vis = editor.getScrolledVisiblePosition(token.pos);
    setOpenList({
      items,
      index: 0,
      top: (vis?.top ?? 24) + (vis?.height ?? 19) + 6,
      left: vis?.left ?? 64,
      line: token.pos.lineNumber,
      from: token.from,
      to: token.to,
      token: token.match,
    });
  }

  function insert(item: CatalogName) {
    const editor = editorRef.current;
    if (!editor) return;
    const live = tokenAtCursor(editor);
    const fallback = listRef.current;
    const line = live?.pos.lineNumber ?? fallback?.line;
    const from = live?.from ?? fallback?.from;
    const to = live?.to ?? fallback?.to;
    const token = live?.match ?? fallback?.token ?? "";
    if (line == null || from == null || to == null) return;
    const text = insertCatalogName(token, item.text);
    editor.executeEdits("names", [
      {
        range: {
          startLineNumber: line,
          startColumn: from,
          endLineNumber: line,
          endColumn: to,
        },
        text,
      },
    ]);
    onChange(editor.getValue());
    closeList();
    editor.focus();
  }

  return (
    <div className="sql-editor" ref={hostRef}>
      <Monaco
        height={height}
        language="sql"
        theme="landata"
        value={value}
        defaultValue={value == null ? defaultValue : undefined}
        onChange={(next) => {
          onChange(next ?? "");
          queueMicrotask(() => {
            const token = editorRef.current ? tokenAtCursor(editorRef.current) : null;
            if (token?.match.startsWith("@") || (token && token.match.length >= 2) || listRef.current) {
              openList();
            } else {
              closeList();
            }
          });
        }}
        onMount={(rawEditor, rawMonaco) => {
          const editor = rawEditor as unknown as EditorApi;
          const monaco = rawMonaco as unknown as MonacoApi;
          editorRef.current = editor;
          monaco.editor.defineTheme("landata", {
            base: "vs-dark",
            inherit: true,
            rules: [],
            colors: {
              "editor.background": "#0b1218",
              "editor.foreground": "#e8eef5",
            },
          });
          monaco.editor.setTheme("landata");
          onReady?.({
            getValue: () => editor.getValue(),
            insertAtCursor: (text) => {
              const live = tokenAtCursor(editor);
              const pos = editor.getPosition();
              if (!pos) return;
              editor.executeEdits("insert", [
                {
                  range: {
                    startLineNumber: live?.pos.lineNumber ?? pos.lineNumber,
                    startColumn: live && live.match ? live.from : pos.column,
                    endLineNumber: live?.pos.lineNumber ?? pos.lineNumber,
                    endColumn: live && live.match ? live.to : pos.column,
                  },
                  text,
                },
              ]);
              onChange(editor.getValue());
            },
          });
          editor.onDidChangeCursorPosition(() => {
            const token = tokenAtCursor(editor);
            if (listRef.current && token?.match) openList();
            else if (!token?.match) closeList();
          });
          editor.onKeyDown((event) => {
            if (event.ctrlKey || event.altKey || event.metaKey) return;
            if (event.keyCode === monaco.KeyCode.Tab) {
              event.preventDefault();
              event.stopPropagation();
              const current = listRef.current;
              if (current && current.items.length > 0) {
                const next = event.shiftKey
                  ? (current.index - 1 + current.items.length) % current.items.length
                  : (current.index + 1) % current.items.length;
                setOpenList({ ...current, index: next });
              } else {
                queueMicrotask(() => openList());
              }
            } else if (event.keyCode === monaco.KeyCode.Enter) {
              const current = listRef.current;
              if (!current || current.items.length === 0) return;
              event.preventDefault();
              event.stopPropagation();
              const item = current.items[current.index];
              if (item) queueMicrotask(() => insert(item));
            } else if (event.keyCode === monaco.KeyCode.Escape) {
              closeList();
            } else if (event.keyCode === monaco.KeyCode.Space) {
              closeList();
            }
          });
        }}
        options={{
          minimap: { enabled: false },
          fontSize: 14,
          fontFamily: "Consolas, 'Courier New', monospace",
          fixedOverflowWidgets: true,
          quickSuggestions: false,
          suggestOnTriggerCharacters: false,
          wordBasedSuggestions: "off",
          tabCompletion: "off",
          acceptSuggestionOnEnter: "off",
          inlineSuggest: { enabled: false },
          suggest: { showWords: false, preview: false, snippetsPreventQuickSuggestions: true },
        }}
      />
      {list && (
        <div className="name-list" style={{ top: list.top, left: Math.max(8, list.left) }} role="listbox" aria-label="Saved names">
          {list.items.length === 0 && (
            <p className="name-list-empty">
              {list.token.startsWith("@")
                ? "No user attributes match this prefix."
                : "No saved names for this prefix. Sync the datasource first."}
            </p>
          )}
          {list.items.map((item, index) => (
            <button
              key={`${item.kind}-${item.text}`}
              type="button"
              className={index === list.index ? "active" : ""}
              onMouseDown={(event) => {
                event.preventDefault();
                insert(item);
              }}
            >
              <span>{item.text}</span>
              <em>{item.detail || item.kind}</em>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
