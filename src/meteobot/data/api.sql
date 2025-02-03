
-- :name user-favs :? :*
select user_id, ts, favs from meteobot_favs where user_id = :user-id;

-- :name save-favs :? :*
insert into meteobot_favs(user_id, ts, favs) 
values (:user-id, CURRENT_TIMESTAMP, :favs)
on conflict (user_id) do 
update set ts=CURRENT_TIMESTAMP, favs=:favs;

-- :name user-subs :? :*
select subs_id, user_id, ts, hhmm, wdays, st 
from meteobot_subs 
where user_id = :user-id order by hhmm, st;

-- :name user-subs-by-id :? :*
select subs_id, user_id, ts, hhmm, wdays, st from meteobot_subs
where subs_id=:subs-id and user_id=:user-id;

-- :name user-subs-create :? :*
insert into meteobot_subs(user_id, ts, hhmm, wdays, st) 
values (:user-id, CURRENT_TIMESTAMP, :hhmm, :wdays, :st)
returning *;

-- :name user-subs-update :? :*
update meteobot_subs set ts=CURRENT_TIMESTAMP, hhmm=:hhmm, wdays=:wdays
where subs_id=:subs-id and user_id=:user-id;

-- :name user-subs-delete :? :*
delete from meteobot_subs where subs_id=:subs-id and user_id=:user-id;
