alter table user_account add username nvarchar(100) null;
GO

update user_account
set username = lower(
    left(
      case
        when charindex(N'@', email) > 1 then left(email, charindex(N'@', email) - 1)
        else left(email, 20)
      end,
      40
    )
  )
  + N'_'
  + left(replace(convert(nvarchar(36), id), N'-', N''), 6)
where username is null or username = N'';
GO

create unique index user_account_username_uq on user_account (username) where username is not null;
GO

alter table membership add branch_ids nvarchar(max) not null
  constraint df_membership_branches default '[]';
