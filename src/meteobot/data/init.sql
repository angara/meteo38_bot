
create table meteobot_favs (
    user_id bigint primary key,
    ts      timestamptz not null,
    favs    jsonb  -- ["uiii","npsd", ...]
);

create table meteobot_subs (
    subs_id bigserial primary key,
    user_id bigint,
    ts      timestamptz not null,
    hhmm    time not null,       -- subs time: 08:10
    wdays   varchar(7) not null, -- week days: "1234567"
    st      varchar(80) not null -- station name
);

create index meteobot_subs__user_id__ix on meteobot_subs(user_id);
create index meteobot_subs__hhmm__ix on meteobot_subs(hhmm);

