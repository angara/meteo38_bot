
-- :name user-favs :? :*
select user_id, ts, favs from meteobot_favs where user_id = :user-id;

-- :name save-favs :? :*
insert into meteobot_favs(user_id, ts, favs) values (:user-id, CURRENT_TIMESTAMP, :favs)
on conflict (user_id) do update set ts=CURRENT_TIMESTAMP, favs=:favs;
