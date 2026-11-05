"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type { Page, Question } from "@/lib/api/types";

export default function QuestionsPage() {
  const list = useQuery({
    queryKey: ["questions"],
    queryFn: () => api<Page<Question> | Question[]>("/api/v1/questions"),
  });
  const items = Array.isArray(list.data) ? list.data : list.data?.items ?? [];

  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div>
          <h1>Questions</h1>
          <p className="muted">Saved queries from the builder or AI accept.</p>
        </div>
        <Link className="btn btn-primary" href="/queries">
          New from SQL
        </Link>
      </div>
      <div className="card">
        {list.isLoading && <p className="muted">Loading…</p>}
        {items.length === 0 && !list.isLoading && <p className="muted">No saved questions.</p>}
        {items.length > 0 && (
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Kind</th>
                <th>Datasource</th>
              </tr>
            </thead>
            <tbody>
              {items.map((q) => (
                <tr key={q.id}>
                  <td>
                    <Link href={`/questions/${q.id}`}>{q.name}</Link>
                  </td>
                  <td>{q.is_parameter ? "Parameter" : "Question"}</td>
                  <td className="mono muted">{q.datasource_id}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
