-- Every invite is for one email address. An accepted invite takes the email of the member who used it, which is
-- already stored the way the app compares emails, and an invite nobody used has no address to give it, so it goes.
alter table invite add column email text;

update invite
set email = member.email
from member
where member.id = invite.accepted_by;

delete from invite where accepted_by is null;

alter table invite alter column email set not null;
