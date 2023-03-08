create TABLE IF NOT EXISTS customer(id UUID, name VARCHAR(100), country_code VARCHAR(20)) PARTITION BY LIST(country_code);

alter table customer add constraint customer_pk primary key(id,country_code);

create TABLE indian_customer PARTITION OF customer FOR VALUES IN ('IN');

create TABLE australian_customer PARTITION OF customer FOR VALUES IN ('AU');
