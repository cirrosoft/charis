-- USERS
INSERT INTO user (fullname, email, username, password) VALUES ('Yes Man', 'mansy@email.com', 'yeser', '$2a$10$jxwA1OmYBTitDdBvrMS4V.nTK6FmuRV9Bwb7S2cVpdwsDRoQ6oPOC');
SET @user_admin = last_insert_id();
INSERT INTO user (fullname, email, username, password) VALUES ('Adam Williams', 'adamw@gmail.com', 'yeserxxy', '$2a$10$eoMnYHCpvsrlhU6EJFc5y.fUlj0TYX.6SRnEMCs8kHw.VWxsk9lgm');
SET @user_user = last_insert_id();
