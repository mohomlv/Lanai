alter table saved_question add is_parameter bit not null
  constraint df_saved_question_is_parameter default 0;

alter table saved_question add parameter_fields nvarchar(max) null;

create table role_parameter_question (
  id uniqueidentifier not null constraint role_parameter_question_pk primary key default newid(),
  company_id uniqueidentifier not null constraint rpq_company_fk references company(id),
  role nvarchar(32) not null,
  question_id uniqueidentifier not null constraint rpq_question_fk references saved_question(id),
  created_at datetimeoffset not null constraint df_rpq_created default sysdatetimeoffset(),
  constraint rpq_role_check check (role in ('owner','admin','editor','viewer')),
  constraint rpq_uq unique (company_id, role, question_id)
);
create index role_parameter_question_role_idx on role_parameter_question (company_id, role);
