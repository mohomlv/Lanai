alter table schema_doc drop constraint schema_doc_kind_check;
alter table schema_doc add constraint schema_doc_kind_check
  check (kind in ('table','database_overview','query_guide'));

alter table schema_doc add title nvarchar(200) null;

drop index schema_doc_current_uq on schema_doc;
create unique index schema_doc_current_uq on schema_doc (datasource_id, table_key, kind)
  where is_current = 1 and kind in ('table', 'database_overview');

alter table doc_embedding drop constraint doc_embedding_kind_check;
alter table doc_embedding add constraint doc_embedding_kind_check
  check (chunk_kind in ('header','columns','relationships','examples','overview','query_guide'));
