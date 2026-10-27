INSERT INTO users (user_id, username, email, password_hash, display_name, enabled)
VALUES (15, 'pending.client', 'pending@example.test', 'hash', 'Pending Client', TRUE);
INSERT INTO user_roles (user_id, role_id) VALUES (15, 5);
