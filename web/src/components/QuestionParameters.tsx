"use client";

import { FormEvent, useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api/client";
import type { Page } from "@/lib/api/types";
import { ParameterBar } from "@/components/ParameterBar";
import type { ParamValue, SqlParam } from "@/lib/sql-params";

const DATA_TYPES = [
  { id: "text", label: "String" },
  { id: "number", label: "Number" },
  { id: "boolean", label: "Boolean" },
  { id: "date", label: "Date" },
  { id: "entity", label: "Entity" },
];

type Fields = { fields?: string[]; value_field?: string; display_field?: string };
type SourceQuestion = { id: string; name: string; parameter_fields?: Fields };

type Draft = {
  name: string;
  type: string;
  label: string;
  selection_mode: "single" | "multi";
  defaultText: string;
  column: string;
  questionId: string;
  sourceField: string;
  displayField: string;
  valueField: string;
  required: boolean;
};

type Props = {
  questionId: string;
  sql: string;
  parameters: SqlParam[];
  values: Record<string, ParamValue>;
  onValue: (name: string, value: ParamValue) => void;
  onSql: (sql: string) => void;
  isParameter: boolean;
  parameterFields?: Fields;
  saveParameters: (parameters: SqlParam[], sql: string) => Promise<void>;
};

function snippet(name: string, type: string, mode: string, column: string) {
  const col = column.trim();
  if (!col) return "";
  if (type === "date_range") return `\n[[ AND ${col} >= {{${name}.start}} AND ${col} < {{${name}.end_exclusive}} ]]`;
  if (mode === "multi" || type === "select_multi") return `\n[[ AND ${col} IN ({{${name}}}) ]]`;
  return `\n[[ AND ${col} = {{${name}}} ]]`;
}

function defaultText(value: unknown): string {
  if (Array.isArray(value)) return value.map((item) => String(item)).join("\n");
  if (typeof value === "boolean") return value ? "true" : "false";
  if (value == null) return "";
  return String(value);
}

function draftFrom(parameter?: SqlParam): Draft {
  const source = parameter?.options_source;
  return {
    name: parameter?.name ?? "",
    type: parameter?.type || "text",
    label: parameter?.label || parameter?.name || "",
    selection_mode: parameter?.selection_mode || (parameter?.type === "select_multi" ? "multi" : "single"),
    defaultText: defaultText(parameter?.default),
    column: "",
    questionId: source?.question_id || "",
    sourceField: source?.source_field || "",
    displayField: source?.display_field || "",
    valueField: source?.value_field || "",
    required: Boolean(parameter?.required),
  };
}

function toParam(draft: Draft): SqlParam {
  const name = draft.name.trim();
  const selectionMode = draft.type === "date_range" ? "single" : draft.selection_mode;
  const param: SqlParam = {
    name,
    type: draft.type,
    label: draft.label.trim() || name,
    required: draft.required,
    selection_mode: selectionMode,
  };
  const lines = draft.defaultText.split("\n").map((item) => item.trim()).filter(Boolean);
  if (selectionMode === "multi") {
    if (lines.length > 0) param.default = lines;
  } else if (draft.type === "boolean") {
    if (draft.defaultText === "true" || draft.defaultText === "false") param.default = draft.defaultText === "true";
  } else if (draft.type === "number" && draft.defaultText.trim()) {
    param.default = Number(draft.defaultText);
  } else if (draft.defaultText.trim()) {
    param.default = draft.defaultText.trim();
  }
  if (draft.questionId) {
    param.options_source = {
      kind: "query",
      question_id: draft.questionId,
      source_field: draft.sourceField || draft.valueField,
      display_field: draft.displayField,
      value_field: draft.valueField,
    };
  }
  return param;
}

export function QuestionParameters({
  questionId,
  sql,
  parameters,
  values,
  onValue,
  onSql,
  isParameter,
  parameterFields,
  saveParameters,
}: Props) {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<string | null>(null);
  const [draft, setDraft] = useState<Draft>(draftFrom());
  const [error, setError] = useState<string | null>(null);
  const [asParameter, setAsParameter] = useState(isParameter);
  const [fields, setFields] = useState<string[]>(parameterFields?.fields ?? []);
  const [fieldName, setFieldName] = useState("");
  const [valueField, setValueField] = useState(parameterFields?.value_field ?? "");
  const [displayField, setDisplayField] = useState(parameterFields?.display_field ?? "");

  const sources = useQuery({
    queryKey: ["parameter-questions"],
    queryFn: () => api<Page<SourceQuestion>>("/api/v1/parameter-questions"),
  });
  const sourceItems = (sources.data?.items ?? []).filter((item) => item.id !== questionId);
  const selectedSource = sourceItems.find((item) => item.id === draft.questionId);
  const sourceFields = selectedSource?.parameter_fields?.fields ?? [];

  useEffect(() => {
    setAsParameter(isParameter);
    setFields(parameterFields?.fields ?? []);
    setValueField(parameterFields?.value_field ?? "");
    setDisplayField(parameterFields?.display_field ?? "");
  }, [isParameter, parameterFields]);

  function openEditor(parameter?: SqlParam) {
    setError(null);
    setDraft(draftFrom(parameter));
    setEditing(parameter?.name ?? "");
  }

  async function saveDraft(e: FormEvent) {
    e.preventDefault();
    const name = draft.name.trim();
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)) {
      setError("Parameter name must be a plain name.");
      return;
    }
    if (draft.type === "entity" && (!draft.questionId || !draft.valueField || !draft.displayField)) {
      setError("An entity parameter needs a source question, a value field, and a display field.");
      return;
    }
    const nextParam = toParam(draft);
    const next = [...parameters.filter((item) => item.name !== (editing || name) && item.name !== name), nextParam];
    const extra = sql.includes(`{{${name}}}`) ? "" : snippet(name, nextParam.type, nextParam.selection_mode || "single", draft.column);
    const nextSql = extra ? `${sql.trim()}${extra}` : sql;
    try {
      await saveParameters(next, nextSql);
      onSql(nextSql);
      setEditing(null);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save parameter");
    }
  }

  async function removeParameter(name: string) {
    if (sql.includes(`{{${name}}}`)) {
      setError("Remove this parameter from the SQL before deleting it.");
      return;
    }
    try {
      await saveParameters(parameters.filter((item) => item.name !== name), sql);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove parameter");
    }
  }

  async function saveSource(e: FormEvent) {
    e.preventDefault();
    try {
      await api(`/api/v1/questions/${questionId}`, {
        method: "PATCH",
        body: JSON.stringify({
          is_parameter: asParameter,
          parameter_fields: {
            fields,
            value_field: valueField,
            display_field: displayField,
          },
        }),
      });
      await qc.invalidateQueries({ queryKey: ["question", questionId] });
      await qc.invalidateQueries({ queryKey: ["parameter-questions"] });
      await qc.invalidateQueries({ queryKey: ["parameter-access"] });
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save parameter label");
    }
  }

  const typeOptions = DATA_TYPES.some((item) => item.id === draft.type)
    ? DATA_TYPES
    : [...DATA_TYPES, { id: draft.type, label: draft.type }];

  return (
    <>
      <section className="card stack">
        <h2>Parameter label</h2>
        <p className="muted">Mark this question so other parameters can use its rows. The field used is the value stored in SQL. The field shown is the label people see.</p>
        <form className="stack" onSubmit={saveSource}>
          <label className="label">
            <span className="row">
              <input type="checkbox" checked={asParameter} onChange={(e) => setAsParameter(e.target.checked)} />
              Use as a parameter source
            </span>
          </label>
          <div className="row">
            <input
              className="input"
              style={{ maxWidth: 220 }}
              placeholder="field name"
              value={fieldName}
              onChange={(e) => setFieldName(e.target.value)}
            />
            <button
              className="btn"
              type="button"
              onClick={() => {
                const name = fieldName.trim();
                if (!name || fields.includes(name)) return;
                setFields((current) => [...current, name]);
                setFieldName("");
              }}
            >
              Add field
            </button>
          </div>
          {fields.length > 0 && (
            <ul>
              {fields.map((name) => (
                <li key={name}>
                  {name}{" "}
                  <button
                    className="btn"
                    type="button"
                    onClick={() => {
                      setFields((current) => current.filter((item) => item !== name));
                      if (valueField === name) setValueField("");
                      if (displayField === name) setDisplayField("");
                    }}
                  >
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          )}
          <label className="label">
            Field used
            <select className="select" value={valueField} onChange={(e) => setValueField(e.target.value)}>
              <option value="">Choose</option>
              {fields.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
          </label>
          <label className="label">
            Field shown
            <select className="select" value={displayField} onChange={(e) => setDisplayField(e.target.value)}>
              <option value="">Choose</option>
              {fields.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
          </label>
          <button className="btn" type="submit">
            Save parameter label
          </button>
        </form>
      </section>

      <section className="card stack">
        <div className="row" style={{ justifyContent: "space-between" }}>
          <h2>Parameters</h2>
          <button className="btn" type="button" onClick={() => openEditor()}>
            Add parameter
          </button>
        </div>
        <p className="muted">Filters for this question. Edit sets the default, the data type, the source, and single or multi select.</p>
        {error && <div className="error-banner">{error}</div>}
        {parameters.length === 0 && <p className="muted">No parameters in the SQL yet.</p>}
        {parameters.length > 0 && (
          <table className="table">
            <thead>
              <tr>
                <th>Label</th>
                <th>Type</th>
                <th>Selection</th>
                <th>Default</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {parameters.map((parameter) => (
                <tr key={parameter.name}>
                  <td>{parameter.label || parameter.name}</td>
                  <td className="mono">{parameter.type}</td>
                  <td>{parameter.selection_mode === "multi" || parameter.type === "select_multi" ? "Multi" : "Single"}</td>
                  <td className="mono">{defaultText(parameter.default) || "—"}</td>
                  <td>
                    <button className="btn" type="button" onClick={() => openEditor(parameter)}>
                      Edit
                    </button>{" "}
                    <button className="btn" type="button" onClick={() => void removeParameter(parameter.name)}>
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {editing !== null && (
          <form className="stack" onSubmit={saveDraft}>
            <h3>{editing ? "Edit parameter" : "New parameter"}</h3>
            <label className="label">
              Name
              <input
                className="input"
                value={draft.name}
                disabled={editing !== ""}
                onChange={(e) => setDraft((current) => ({ ...current, name: e.target.value }))}
              />
            </label>
            <label className="label">
              Label
              <input
                className="input"
                value={draft.label}
                onChange={(e) => setDraft((current) => ({ ...current, label: e.target.value }))}
              />
            </label>
            <label className="label">
              Data type
              <select
                className="select"
                value={draft.type}
                onChange={(e) => setDraft((current) => ({ ...current, type: e.target.value }))}
              >
                {typeOptions.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.label}
                  </option>
                ))}
              </select>
            </label>
            {draft.type !== "date_range" && (
              <label className="label">
                Selection
                <select
                  className="select"
                  value={draft.selection_mode}
                  onChange={(e) =>
                    setDraft((current) => ({ ...current, selection_mode: e.target.value === "multi" ? "multi" : "single" }))
                  }
                >
                  <option value="single">Single</option>
                  <option value="multi">Multi</option>
                </select>
              </label>
            )}
            <label className="label">
              Default
              {draft.selection_mode === "multi" ? (
                <textarea
                  className="input"
                  rows={3}
                  placeholder="One value per line"
                  value={draft.defaultText}
                  onChange={(e) => setDraft((current) => ({ ...current, defaultText: e.target.value }))}
                />
              ) : draft.type === "boolean" ? (
                <select
                  className="select"
                  value={draft.defaultText}
                  onChange={(e) => setDraft((current) => ({ ...current, defaultText: e.target.value }))}
                >
                  <option value="">None</option>
                  <option value="true">True</option>
                  <option value="false">False</option>
                </select>
              ) : (
                <input
                  className="input"
                  type={draft.type === "number" ? "number" : draft.type === "date" ? "date" : "text"}
                  value={draft.defaultText}
                  onChange={(e) => setDraft((current) => ({ ...current, defaultText: e.target.value }))}
                />
              )}
            </label>
            <label className="label">
              SQL column
              <input
                className="input"
                placeholder="orders.city_id"
                value={draft.column}
                onChange={(e) => setDraft((current) => ({ ...current, column: e.target.value }))}
              />
            </label>
            <label className="label">
              Source question
              <select
                className="select"
                value={draft.questionId}
                onChange={(e) => {
                  const nextId = e.target.value;
                  const next = sourceItems.find((item) => item.id === nextId);
                  setDraft((current) => ({
                    ...current,
                    questionId: nextId,
                    valueField: next?.parameter_fields?.value_field || "",
                    displayField: next?.parameter_fields?.display_field || "",
                    sourceField: next?.parameter_fields?.value_field || "",
                  }));
                }}
              >
                <option value="">None</option>
                {sourceItems.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
              {sourceItems.length === 0 && (
                <span className="muted">No parameter questions are available for your role.</span>
              )}
            </label>
            {draft.questionId && (
              <>
                <label className="label">
                  Source field
                  <select
                    className="select"
                    value={draft.sourceField}
                    onChange={(e) => setDraft((current) => ({ ...current, sourceField: e.target.value }))}
                  >
                    <option value="">Choose</option>
                    {sourceFields.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="label">
                  Display field
                  <select
                    className="select"
                    value={draft.displayField}
                    onChange={(e) => setDraft((current) => ({ ...current, displayField: e.target.value }))}
                  >
                    <option value="">Choose</option>
                    {sourceFields.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="label">
                  Value field
                  <select
                    className="select"
                    value={draft.valueField}
                    onChange={(e) => setDraft((current) => ({ ...current, valueField: e.target.value }))}
                  >
                    <option value="">Choose</option>
                    {sourceFields.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                  </select>
                </label>
              </>
            )}
            <div className="row">
              <button className="btn btn-primary" type="submit">
                Save parameter
              </button>
              <button className="btn" type="button" onClick={() => setEditing(null)}>
                Cancel
              </button>
            </div>
          </form>
        )}
        <ParameterBar parameters={parameters} values={values} onChange={onValue} />
      </section>
    </>
  );
}
