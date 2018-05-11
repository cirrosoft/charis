CREATE TABLE user (
id BIGINT NOT NULL AUTO_INCREMENT,
username VARCHAR(80) NOT NULL,
email VARCHAR(255) NOT NULL,
password VARCHAR(255) NOT NULL,
fullname VARCHAR(255),
description TEXT,
image_path TEXT,
date_created DATETIME DEFAULT CURRENT_TIMESTAMP,
date_modified DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
date_last_login DATETIME,
PRIMARY KEY (id),
UNIQUE (username),
UNIQUE (email)
);

CREATE TABLE role (
id BIGINT NOT NULL AUTO_INCREMENT,
name VARCHAR(80) NOT NULL,
description TEXT,
PRIMARY KEY (id),
UNIQUE (name)
);

CREATE TABLE role_x_user (
id BIGINT NOT NULL AUTO_INCREMENT,
role_id BIGINT NOT NULL,
user_id BIGINT NOT NULL,
PRIMARY KEY (id, role_id, user_id)
);



