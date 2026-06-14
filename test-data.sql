-- TechDecide test data
-- Password for all users: Test1234!
-- BCrypt hash: $2a$10$x3xBA9.UOGozNFiwF9opfuOGvpczzuDukKdhwgxycpTrmV9BbPSci
-- Run order: org -> teams -> users -> memberships -> projects

-- Organization
INSERT INTO organizations (id, name, created_at)
VALUES (1, 'TechDecide Corp', NOW())
ON CONFLICT (id) DO NOTHING;

-- Teams
INSERT INTO teams (id, name, organization_id, created_at)
VALUES (1, 'Backend Team', 1, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO teams (id, name, organization_id, created_at)
VALUES (2, 'Devops Team', 1, NOW())
ON CONFLICT (id) DO NOTHING;

-- Users
INSERT INTO users (id, email, name, password, app_role, created_at)
VALUES (1, 'admin@techdecide.com', 'App Admin', '$2a$10$x3xBA9.UOGozNFiwF9opfuOGvpczzuDukKdhwgxycpTrmV9BbPSci', 'APP_ADMIN', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, name, password, app_role, created_at)
VALUES (2, 'teamadmin.backend@techdecide.com', 'Backend Lead', '$2a$10$x3xBA9.UOGozNFiwF9opfuOGvpczzuDukKdhwgxycpTrmV9BbPSci', 'USER', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, name, password, app_role, created_at)
VALUES (3, 'member.backend@techdecide.com', 'Backend Member', '$2a$10$x3xBA9.UOGozNFiwF9opfuOGvpczzuDukKdhwgxycpTrmV9BbPSci', 'USER', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, name, password, app_role, created_at)
VALUES (4, 'teamadmin.devops@techdecide.com', 'Devops Lead', '$2a$10$x3xBA9.UOGozNFiwF9opfuOGvpczzuDukKdhwgxycpTrmV9BbPSci', 'USER', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, name, password, app_role, created_at)
VALUES (5, 'member.devops@techdecide.com', 'Devops Member', '$2a$10$x3xBA9.UOGozNFiwF9opfuOGvpczzuDukKdhwgxycpTrmV9BbPSci', 'USER', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, name, password, app_role, created_at)
VALUES (6, 'noteam@techdecide.com', 'No Team User', '$2a$10$x3xBA9.UOGozNFiwF9opfuOGvpczzuDukKdhwgxycpTrmV9BbPSci', 'USER', NOW())
ON CONFLICT (id) DO NOTHING;

-- Sync sequences so future inserts don't collide with seeded ids
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('teams_id_seq', (SELECT MAX(id) FROM teams));
SELECT setval('organizations_id_seq', (SELECT MAX(id) FROM organizations));

-- Team memberships (idempotent — no unique constraint on the table)
INSERT INTO team_memberships (user_id, team_id, team_role, created_at)
SELECT 2, 1, 'TEAM_ADMIN', NOW()
WHERE NOT EXISTS (SELECT 1 FROM team_memberships WHERE user_id = 2 AND team_id = 1);

INSERT INTO team_memberships (user_id, team_id, team_role, created_at)
SELECT 3, 1, 'MEMBER', NOW()
WHERE NOT EXISTS (SELECT 1 FROM team_memberships WHERE user_id = 3 AND team_id = 1);

INSERT INTO team_memberships (user_id, team_id, team_role, created_at)
SELECT 4, 2, 'TEAM_ADMIN', NOW()
WHERE NOT EXISTS (SELECT 1 FROM team_memberships WHERE user_id = 4 AND team_id = 2);

INSERT INTO team_memberships (user_id, team_id, team_role, created_at)
SELECT 5, 2, 'MEMBER', NOW()
WHERE NOT EXISTS (SELECT 1 FROM team_memberships WHERE user_id = 5 AND team_id = 2);

-- Projects
INSERT INTO projects (id, name, description, organization_id, created_at, updated_at)
VALUES (1, 'GTN', 'GTN product project', 1, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

SELECT setval('projects_id_seq', (SELECT MAX(id) FROM projects));

-- Project-Team assignments (idempotent — no unique constraint on the table)
INSERT INTO project_teams (project_id, team_id, created_at)
SELECT 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM project_teams WHERE project_id = 1 AND team_id = 1);

INSERT INTO project_teams (project_id, team_id, created_at)
SELECT 1, 2, NOW()
WHERE NOT EXISTS (SELECT 1 FROM project_teams WHERE project_id = 1 AND team_id = 2);
