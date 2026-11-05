"use client";

import { useRouter } from "next/navigation";
import { DatasourceForm } from "@/components/DatasourceForm";

export default function NewDatasourcePage() {
  const router = useRouter();
  return (
    <div className="stack">
      <h1>Add datasource</h1>
      <DatasourceForm
        onSaved={(ds, scrapeJobId) =>
          router.replace(scrapeJobId ? `/datasources/${ds.id}?scrape=${scrapeJobId}` : `/datasources/${ds.id}`)
        }
      />
    </div>
  );
}
