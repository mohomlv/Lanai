"use client";

import { FormEvent, useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api/client";

const ROLES = ["owner", "admin", "editor", "viewer"] as const;

type AccessQuestion = { id: string; name: string };
type Grant = { role: string; question_id: string };
type AccessBody = { questions: AccessQuestion[]; grants: Grant[] };

export function ParameterAccessEditor() {
  const qc = useQueryClient();
  const [picked, setPicked] = useState<Record<string, string[]>>({});
  const [error, setError] = useState<string | null>(null);
  const access = useQuery({
    queryKey: ["parameter-access"],
    queryFn: () => api<AccessBody>("/api/v1/parameter-access"),
  });

  useEffect(() => {
    if (!access.data) return;
    const next: Record<string, string[]> = {};
    for (const role of ROLES) next[role] = [];
    for (const grant of access.data.grants ?? []) {
      if (!next[grant.role]) next[grant.role] = [];
      next[grant.role].push(grant.question_id);
    }
    setPicked(next);
  }, [access.data]);

  const save = useMutation({
    mutationFn: () => {
      const grants = ROLES.flatMap((role) => (picked[role] ?? []).map((questionId) => ({ role, question_id: questionId })));
      return api("/api/v1/parameter-access", { method: "PUT", body: JSON.stringify({ grants }) });
    },
    onSuccess: async () => {
      setError(null);
      await qc.invalidateQueries({ queryKey: ["parameter-access"] });
      await qc.invalidateQueries({ queryKey: ["parameter-questions"] });
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "Could not save parameter access"),
  });

  const questions = access.data?.questions ?? [];

  function toggle(role: string, questionId: string) {
    setPicked((current) => {
      const selected = current[role] ?? [];
      const next = selected.includes(questionId)
        ? selected.filter((id) => id !== questionId)
        : [...selected, questionId];
      return { ...current, [role]: next };
    });
  }

  return (
    <section className="card stack">
      <h2>Parameter access</h2>
      <p className="muted">
        Each role may use only the parameter questions checked here. The server rejects any other source, including direct requests.
      </p>
      {access.isLoading && <p className="muted">Loading…</p>}
      {access.isError && (
        <div className="error-banner">
          {access.error instanceof ApiError ? access.error.message : "Parameter access is not available"}
        </div>
      )}
      {error && <div className="error-banner">{error}</div>}
      {questions.length === 0 && !access.isLoading && (
        <p className="muted">No questions are marked as parameter sources yet.</p>
      )}
      {questions.length > 0 &&
        ROLES.map((role) => (
          <fieldset key={role} className="stack">
            <legend>{role}</legend>
            {questions.map((question) => (
              <label key={question.id} className="row">
                <input
                  type="checkbox"
                  checked={(picked[role] ?? []).includes(question.id)}
                  onChange={() => toggle(role, question.id)}
                />
                {question.name}
              </label>
            ))}
          </fieldset>
        ))}
      <form
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <button className="btn btn-primary" type="submit" disabled={save.isPending || questions.length === 0}>
          Save parameter access
        </button>
      </form>
    </section>
  );
}
