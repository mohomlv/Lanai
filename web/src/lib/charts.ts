import type { QueryResult } from "@/lib/api/types";

export const CHARTS: { key: string; label: string }[] = [
  { key: "table", label: "Table" },
  { key: "scalar", label: "Number" },
  { key: "scalar_comparison", label: "Number vs previous" },
  { key: "gauge", label: "Gauge" },
  { key: "pie", label: "Pie" },
  { key: "bar", label: "Bar" },
  { key: "stacked_bar", label: "Stacked bar" },
  { key: "row", label: "Row" },
  { key: "line", label: "Line" },
  { key: "area", label: "Area" },
  { key: "cumulative_line", label: "Cumulative line" },
  { key: "combo", label: "Combo" },
  { key: "scatter", label: "Scatter" },
  { key: "histogram", label: "Histogram" },
  { key: "funnel", label: "Funnel" },
  { key: "heatmap", label: "Heatmap" },
  { key: "waterfall", label: "Waterfall" },
  { key: "cohort", label: "Cohort" },
  { key: "pivot", label: "Pivot" },
];

const text = { color: "#e8eef5" };

function col(result: QueryResult, ...names: string[]): number {
  const want = names.map((name) => name.toLowerCase());
  return result.columns.findIndex((column) => want.includes(column.name.toLowerCase()));
}

function numbers(result: QueryResult, index: number): number[] {
  return result.rows.map((row) => {
    const value = Number(row[index]);
    return Number.isFinite(value) ? value : 0;
  });
}

function labels(result: QueryResult, index: number): string[] {
  return result.rows.map((row) => String(row[index] ?? ""));
}

function seriesColumns(result: QueryResult): { index: number; name: string }[] {
  const found: { index: number; name: string }[] = [];
  result.columns.forEach((column, index) => {
    const match = /^p(\d+)$/i.exec(column.name);
    if (!match) return;
    const titleIdx = col(result, `p${match[1]}_title`);
    const title = titleIdx >= 0 ? String(result.rows[0]?.[titleIdx] ?? column.name) : column.name;
    found.push({ index, name: title });
  });
  return found;
}

export function scalarText(result: QueryResult): string | null {
  const index = col(result, "value", "amount");
  const cell = result.rows[0]?.[index >= 0 ? index : 0];
  return cell == null ? null : String(cell);
}

export function chartOption(template: string, result: QueryResult): object | null {
  if (!result.columns.length || !result.rows.length) return null;
  if (template === "table" || template === "scalar" || template === "scalar_comparison") return null;
  const titleIdx = col(result, "title");
  const amountIdx = col(result, "amount", "value");
  const dateIdx = col(result, "date", "category");
  const labelIdx = titleIdx >= 0 ? titleIdx : dateIdx >= 0 ? dateIdx : 0;
  const valueIdx = amountIdx >= 0 ? amountIdx : result.columns.length > 1 ? 1 : 0;
  const names = labels(result, labelIdx);
  const values = numbers(result, valueIdx);
  const tooltip = { trigger: "item" as const, textStyle: text };
  if (template === "pie" || template === "funnel") {
    return {
      tooltip,
      series: [
        {
          type: template === "funnel" ? "funnel" : "pie",
          data: names.map((name, i) => ({ name, value: values[i] })),
        },
      ],
    };
  }
  if (template === "gauge") {
    return {
      series: [{ type: "gauge", data: [{ value: values[0], name: names[0] || result.columns[valueIdx]?.name || "" }] }],
    };
  }
  if (template === "scatter") {
    const xIdx = col(result, "x", "p1") >= 0 ? col(result, "x", "p1") : 0;
    const yIdx = col(result, "y", "p2") >= 0 ? col(result, "y", "p2") : result.columns.length > 1 ? 1 : 0;
    return {
      tooltip,
      xAxis: { type: "value", axisLabel: text },
      yAxis: { type: "value", axisLabel: text },
      series: [{ type: "scatter", data: result.rows.map((_, i) => [numbers(result, xIdx)[i], numbers(result, yIdx)[i]]) }],
    };
  }
  const named = seriesColumns(result);
  const axisLabels = dateIdx >= 0 ? labels(result, dateIdx) : names;
  const extra =
    named.length > 0
      ? named.map((series, index) => ({
          name: series.name,
          type: template === "combo" && index > 0 ? "line" : template === "line" || template === "area" || template === "cumulative_line" ? "line" : "bar",
          stack: template === "stacked_bar" || template === "area" ? "total" : undefined,
          areaStyle: template === "area" ? {} : undefined,
          data: numbers(result, series.index),
        }))
      : result.columns
          .map((column, index) => ({ column, index }))
          .filter((item) => item.index !== labelIdx && item.index !== dateIdx && !/_title$/i.test(item.column.name))
          .map((item, index) => ({
            name: item.column.name,
            type: template === "combo" && index > 0 ? "line" : template === "line" || template === "area" || template === "cumulative_line" ? "line" : "bar",
            stack: template === "stacked_bar" || template === "area" ? "total" : undefined,
            areaStyle: template === "area" ? {} : undefined,
            data: numbers(result, item.index),
          }));
  const lineLike = template === "line" || template === "area" || template === "cumulative_line";
  const series =
    extra.length > 0
      ? extra
      : [{ type: lineLike ? "line" : "bar", data: values, areaStyle: template === "area" ? {} : undefined }];
  const horizontal = template === "row";
  return {
    tooltip: { trigger: "axis", textStyle: text },
    legend: series.length > 1 ? { textStyle: text } : undefined,
    xAxis: horizontal ? { type: "value", axisLabel: text } : { type: "category", data: axisLabels, axisLabel: text },
    yAxis: horizontal ? { type: "category", data: axisLabels, axisLabel: text } : { type: "value", axisLabel: text },
    series,
  };
}
