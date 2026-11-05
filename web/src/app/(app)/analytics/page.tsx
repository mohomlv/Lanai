"use client";

import Link from "next/link";
import { FormEvent, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api/client";
import type { Dashboard, Page, Question } from "@/lib/api/types";

type Folder = { id: string; name: string; parent_id?: string; description?: string };

export default function AnalyticsPage() {
  const qc = useQueryClient();
  const [folderId, setFolderId] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);

  const folders = useQuery({
    queryKey: ["collections"],
    queryFn: () => api<Page<Folder> | Folder[]>("/api/v1/collections"),
  });
  const questions = useQuery({
    queryKey: ["questions"],
    queryFn: () => api<Page<Question> | Question[]>("/api/v1/questions"),
  });
  const dashboards = useQuery({
    queryKey: ["dashboards"],
    queryFn: () => api<Page<Dashboard & { collection_id?: string }> | (Dashboard & { collection_id?: string })[]>("/api/v1/dashboards"),
  });

  const folderItems = Array.isArray(folders.data) ? folders.data : folders.data?.items ?? [];
  const questionItems = Array.isArray(questions.data) ? questions.data : questions.data?.items ?? [];
  const dashboardItems = Array.isArray(dashboards.data) ? dashboards.data : dashboards.data?.items ?? [];

  const children = useMemo(
    () => folderItems.filter((folder) => (folder.parent_id || "") === folderId),
    [folderItems, folderId],
  );
  const crumbs = useMemo(() => {
    const trail: Folder[] = [];
    let current = folderItems.find((folder) => folder.id === folderId);
    while (current) {
      trail.unshift(current);
      current = folderItems.find((folder) => folder.id === (current?.parent_id || ""));
    }
    return trail;
  }, [folderItems, folderId]);
  const hereQuestions = questionItems.filter((question) => (question.collection_id || "") === folderId);
  const hereDashboards = dashboardItems.filter((dashboard) => (dashboard.collection_id || "") === folderId);

  async function createFolder(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api("/api/v1/collections", {
        method: "POST",
        body: JSON.stringify({ name, parent_id: folderId || undefined }),
      });
      setName("");
      qc.invalidateQueries({ queryKey: ["collections"] });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not create folder");
    }
  }

  async function createDashboard() {
    setError(null);
    try {
      const created = await api<Dashboard>("/api/v1/dashboards", {
        method: "POST",
        body: JSON.stringify({ name: "New dashboard", collection_id: folderId || undefined }),
      });
      if (created.id) window.location.href = `/dashboards/${created.id}`;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not create the dashboard");
    }
  }

  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: "space-between", alignItems: "flex-start" }}>
        <div>
          <h1>Folders</h1>
          <p className="muted">Create folders and subfolders. Questions and dashboards live in the folder you have open.</p>
        </div>
      </div>
      {error && <div className="error-banner">{error}</div>}

      <div className="row" style={{ flexWrap: "wrap" }}>
        <button type="button" className={`btn${!folderId ? " btn-primary" : ""}`} onClick={() => setFolderId("")}>
          All
        </button>
        {crumbs.map((folder) => (
          <button key={folder.id} type="button" className="btn btn-primary" onClick={() => setFolderId(folder.id)}>
            {folder.name}
          </button>
        ))}
      </div>

      <form className="row" onSubmit={createFolder}>
        <input className="input" style={{ maxWidth: 240 }} placeholder="New folder name" value={name} onChange={(e) => setName(e.target.value)} required />
        <button className="btn" type="submit">
          Add folder
        </button>
        <Link className="btn btn-primary" href={`/queries?collection=${folderId}`}>
          New question
        </Link>
      </form>
      <div className="row">
        <button type="button" className="btn" onClick={() => void createDashboard()}>
          New dashboard
        </button>
      </div>

      <div className="grid-2">
        <section className="card stack">
          <h2>Folders</h2>
          {children.length === 0 && <p className="muted">No folders here.</p>}
          {children.map((folder) => (
            <button key={folder.id} type="button" className="btn" onClick={() => setFolderId(folder.id)}>
              {folder.name}
            </button>
          ))}
        </section>
        <section className="card stack">
          <h2>Questions</h2>
          {hereQuestions.length === 0 && <p className="muted">No questions in this folder.</p>}
          {hereQuestions.map((question) => (
            <Link key={question.id} href={`/questions/${question.id}`}>
              {question.name}
            </Link>
          ))}
          <h2>Dashboards</h2>
          {hereDashboards.length === 0 && <p className="muted">No dashboards in this folder.</p>}
          {hereDashboards.map((dashboard) => (
            <Link key={dashboard.id} href={`/dashboards/${dashboard.id}`}>
              {dashboard.name}
            </Link>
          ))}
        </section>
      </div>
    </div>
  );
}
