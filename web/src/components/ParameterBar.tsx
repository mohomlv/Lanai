"use client";

import { useQuery } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api/client";
import type { SqlParam, ParamValue } from "@/lib/sql-params";

type Props = {
  parameters: SqlParam[];
  values: Record<string, ParamValue>;
  onChange: (name: string, value: ParamValue) => void;
};

type OptionItem = { value: string; label: string };

function rangeParts(value: string): { start: string; end: string } {
  if (value.startsWith("preset:")) return { start: "", end: "" };
  const [start = "", end = ""] = value.split("~");
  return { start, end };
}

function shown(parameter: SqlParam, raw: ParamValue | undefined): ParamValue {
  if (raw !== undefined) return raw;
  const fallback = parameter.default;
  if (Array.isArray(fallback)) return fallback.map((item) => String(item));
  if (typeof fallback === "boolean") return fallback ? "true" : "false";
  if (fallback == null) return "";
  return String(fallback);
}

function isMulti(parameter: SqlParam): boolean {
  return parameter.selection_mode === "multi" || parameter.type === "select_multi";
}

export function ParameterBar({ parameters, values, onChange }: Props) {
  if (parameters.length === 0) return null;
  return (
    <div className="param-bar">
      {parameters.map((parameter) => (
        <ParameterControl
          key={parameter.name}
          parameter={parameter}
          value={shown(parameter, values[parameter.name])}
          onChange={onChange}
        />
      ))}
    </div>
  );
}

function ParameterControl({
  parameter,
  value,
  onChange,
}: {
  parameter: SqlParam;
  value: ParamValue;
  onChange: (name: string, value: ParamValue) => void;
}) {
  const source = parameter.options_source;
  const questionId = source?.kind === "query" ? source.question_id : undefined;
  const options = useQuery({
    queryKey: ["parameter-options", questionId, source?.value_field, source?.display_field],
    enabled: Boolean(questionId && source?.value_field && source?.display_field),
    queryFn: () => {
      const query = new URLSearchParams({
        value_field: source?.value_field ?? "",
        display_field: source?.display_field ?? "",
      });
      return api<{ items: OptionItem[] }>(`/api/v1/parameter-questions/${questionId}/options?${query.toString()}`);
    },
  });

  const staticChoices = source?.kind === "static" && Array.isArray(source.values)
    ? source.values.map((item) => ({ value: String(item), label: String(item) }))
    : [];
  const choices = questionId ? options.data?.items ?? [] : staticChoices;
  const text = Array.isArray(value) ? value.join("\n") : value;
  const label = parameter.label || parameter.name;

  if (parameter.type === "boolean" && !isMulti(parameter)) {
    return (
      <label className="label">
        {label}
        <input
          type="checkbox"
          checked={text === "true"}
          onChange={(e) => onChange(parameter.name, e.target.checked ? "true" : "false")}
        />
      </label>
    );
  }
  if (parameter.type === "date" && !isMulti(parameter)) {
    return (
      <label className="label">
        {label}
        <input className="input" type="date" value={text} onChange={(e) => onChange(parameter.name, e.target.value)} />
      </label>
    );
  }
  if (parameter.type === "date_range") {
    const range = rangeParts(text);
    return (
      <label className="label">
        {label}
        <span className="row" style={{ gap: "0.4rem" }}>
          <input
            className="input"
            type="date"
            value={range.start}
            onChange={(e) => onChange(parameter.name, `${e.target.value}~${range.end}`)}
          />
          <input
            className="input"
            type="date"
            value={range.end}
            onChange={(e) => onChange(parameter.name, `${range.start}~${e.target.value}`)}
          />
        </span>
      </label>
    );
  }
  if (parameter.type === "number" && !isMulti(parameter) && choices.length === 0) {
    return (
      <label className="label">
        {label}
        <input className="input" type="number" value={text} onChange={(e) => onChange(parameter.name, e.target.value)} />
      </label>
    );
  }
  if (questionId && options.isError) {
    const message = options.error instanceof ApiError ? options.error.message : "Options are not available";
    return (
      <label className="label">
        {label}
        <span className="muted">{message}</span>
      </label>
    );
  }
  if (choices.length > 0 || questionId) {
    const selected = Array.isArray(value) ? value : text ? [text] : [];
    if (isMulti(parameter)) {
      return (
        <label className="label">
          {label}
          <select
            className="select"
            multiple
            size={Math.min(6, Math.max(choices.length, 3))}
            value={selected}
            onChange={(e) => onChange(parameter.name, Array.from(e.target.selectedOptions).map((option) => option.value))}
          >
            {choices.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      );
    }
    return (
      <label className="label">
        {label}
        <select className="select" value={text} onChange={(e) => onChange(parameter.name, e.target.value)}>
          <option value="">All</option>
          {choices.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
          {text && !choices.some((option) => option.value === text) && <option value={text}>{text}</option>}
        </select>
      </label>
    );
  }
  if (isMulti(parameter)) {
    return (
      <label className="label">
        {label}
        <textarea
          className="input"
          rows={3}
          placeholder="One value per line"
          value={text}
          onChange={(e) => onChange(parameter.name, e.target.value.split("\n"))}
        />
      </label>
    );
  }
  return (
    <label className="label">
      {label}
      <input className="input" value={text} onChange={(e) => onChange(parameter.name, e.target.value)} />
    </label>
  );
}
