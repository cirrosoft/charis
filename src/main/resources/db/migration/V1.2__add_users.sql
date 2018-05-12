-- USERS
INSERT INTO user (fullname, email, username, password) VALUES ('admin admin', 'admin@email.com', 'admin', '$2a$10$jxwA1OmYBTitDdBvrMS4V.nTK6FmuRV9Bwb7S2cVpdwsDRoQ6oPOC');
SET @user_admin = last_insert_id();
INSERT INTO user (fullname, email, username, password) VALUES ('William Buxley', 'willbux@email.com', 'Willbux', '$2a$10$eoMnYHCpvsrlhU6EJFc5y.fUlj0TYX.6SRnEMCs8kHw.VWxsk9lgm');
SET @user_user = last_insert_id();

-- ROLES
INSERT INTO role (name) VALUES ('admin');
SET @role_admin = last_insert_id();
INSERT INTO role (name) VALUES ('user');
SET @role_user = last_insert_id();

-- ROLE ASSIGNMENTS
INSERT INTO role_x_user (role_id, user_id) VALUES (@role_admin, @user_user);
INSERT INTO role_x_user (role_id, user_id) VALUES (@role_admin, @user_admin);
INSERT INTO role_x_user (role_id, user_id) VALUES (@role_user, @user_user);