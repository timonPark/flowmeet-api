-- 조직 지시 및 업무 흐름 관리 플랫폼
-- PostgreSQL schema

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TYPE system_permission AS ENUM ('ADMIN', 'USER');
CREATE TYPE retention_policy AS ENUM ('NORMAL', 'PERMANENT');
CREATE TYPE meeting_status AS ENUM ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');
CREATE TYPE meeting_processing_stage AS ENUM ('NONE', 'AUDIO_UPLOADED', 'TRANSCRIPTION_PENDING', 'TRANSCRIBING', 'SPEAKER_MAPPING_REQUIRED', 'AI_ANALYZING', 'REVIEW_REQUIRED', 'FAILED', 'ARCHIVED');
CREATE TYPE meeting_creation_mode AS ENUM ('IMMEDIATE', 'SCHEDULED');
CREATE TYPE meeting_repeat_rule AS ENUM ('NONE', 'WEEKLY', 'MONTHLY');
CREATE TYPE meeting_location_type AS ENUM ('OFFLINE', 'ONLINE', 'HYBRID');
CREATE TYPE meeting_role_type AS ENUM ('INSTRUCTOR', 'REVIEWER', 'PERFORMER');
CREATE TYPE assignee_type AS ENUM ('USER', 'TEAM');
CREATE TYPE task_type AS ENUM ('TODO', 'DONE');
CREATE TYPE task_status AS ENUM ('DIRECTED', 'CONFIRMED', 'PERFORMING', 'COMPLETION_REQUESTED', 'APPROVED', 'REJECTED');
CREATE TYPE processing_job_type AS ENUM ('TRANSCRIPTION', 'ANALYSIS');
CREATE TYPE processing_status AS ENUM ('WAITING', 'PROCESSING', 'SPEAKER_MAPPING_REQUIRED', 'REVIEW_REQUIRED', 'COMPLETED', 'FAILED');
CREATE TYPE notification_type AS ENUM ('MEETING_REMINDER', 'AI_COMPLETED', 'APPROVAL_REQUESTED', 'TASK_APPROVED', 'TASK_REJECTED', 'PROCESSING_FAILED');

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    parent_team_id UUID REFERENCES teams(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_teams_parent_name UNIQUE (parent_team_id, name)
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    team_id UUID REFERENCES teams(id) ON DELETE SET NULL,
    role_id UUID REFERENCES roles(id) ON DELETE SET NULL,
    manager_id UUID REFERENCES users(id) ON DELETE SET NULL,
    system_permission system_permission NOT NULL DEFAULT 'USER',
    temporary_password BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE buildings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    address TEXT,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (name)
);

CREATE TABLE locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    building_id UUID NOT NULL REFERENCES buildings(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    room_name VARCHAR(150),
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (building_id, name, room_name)
);

CREATE TABLE meetings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    purpose TEXT,
    creation_mode meeting_creation_mode NOT NULL DEFAULT 'IMMEDIATE',
    scheduled_start_at TIMESTAMPTZ,
    scheduled_end_at TIMESTAMPTZ,
    repeat_rule meeting_repeat_rule NOT NULL DEFAULT 'NONE',
    reminder_minutes INTEGER NOT NULL DEFAULT 10,
    location_type meeting_location_type NOT NULL DEFAULT 'OFFLINE',
    location_id UUID REFERENCES locations(id) ON DELETE SET NULL,
    venue VARCHAR(255),
    meeting_url TEXT,
    location_memo TEXT,
    organizer_id UUID NOT NULL REFERENCES users(id),
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    status meeting_status NOT NULL DEFAULT 'SCHEDULED',
    processing_stage meeting_processing_stage NOT NULL DEFAULT 'NONE',
    retention_policy retention_policy NOT NULL DEFAULT 'NORMAL',
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_meeting_time CHECK (ended_at IS NULL OR started_at IS NULL OR ended_at >= started_at),
    CONSTRAINT chk_scheduled_time CHECK (scheduled_end_at IS NULL OR scheduled_start_at IS NULL OR scheduled_end_at >= scheduled_start_at),
    CONSTRAINT chk_reminder_minutes CHECK (reminder_minutes >= 0),
    CONSTRAINT chk_scheduled_range CHECK (scheduled_start_at IS NULL OR scheduled_end_at IS NOT NULL)
);

-- 동일 오프라인 장소의 시간대 중복 예약 방지
ALTER TABLE meetings ADD CONSTRAINT meetings_no_venue_overlap
    EXCLUDE USING gist (
        location_id WITH =,
        tstzrange(scheduled_start_at, scheduled_end_at, '[)') WITH &&
    ) WHERE (status = 'SCHEDULED' AND location_id IS NOT NULL AND scheduled_start_at IS NOT NULL AND scheduled_end_at IS NOT NULL);

-- 동일 온라인 회의 링크의 시간대 중복 예약 방지
ALTER TABLE meetings ADD CONSTRAINT meetings_no_url_overlap
    EXCLUDE USING gist (
        meeting_url WITH =,
        tstzrange(scheduled_start_at, scheduled_end_at, '[)') WITH &&
    ) WHERE (status = 'SCHEDULED' AND meeting_url IS NOT NULL AND scheduled_start_at IS NOT NULL AND scheduled_end_at IS NOT NULL);

CREATE TABLE meeting_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (meeting_id, user_id)
);

CREATE TABLE meeting_role_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    role_type meeting_role_type NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    team_id UUID REFERENCES teams(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_meeting_role_target CHECK ((user_id IS NOT NULL) <> (team_id IS NOT NULL)),
    UNIQUE (meeting_id, role_type, user_id, team_id)
);

CREATE TABLE audio_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL UNIQUE,
    file_path TEXT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    duration_seconds INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_audio_duration CHECK (duration_seconds BETWEEN 1 AND 3600),
    CONSTRAINT chk_audio_size CHECK (file_size > 0 AND file_size <= 524288000)
);

CREATE TABLE transcripts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL UNIQUE REFERENCES meetings(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    speaker_content JSONB,
    provider VARCHAR(50) NOT NULL DEFAULT 'DEEPGRAM',
    status processing_status NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE speaker_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    speaker_label VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    matched_by UUID REFERENCES users(id) ON DELETE SET NULL,
    matched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (meeting_id, speaker_label),
    UNIQUE (meeting_id, user_id)
);

CREATE TABLE meeting_contents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL UNIQUE REFERENCES meetings(id) ON DELETE CASCADE,
    summary TEXT,
    purpose TEXT,
    full_content TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_processing_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    job_type processing_job_type NOT NULL,
    status processing_status NOT NULL DEFAULT 'WAITING',
    provider VARCHAR(50) NOT NULL,
    external_job_id VARCHAR(255),
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_retry_count CHECK (retry_count BETWEEN 0 AND 3)
);

CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    task_type task_type NOT NULL DEFAULT 'TODO',
    assignee_type assignee_type NOT NULL,
    assignee_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    assignee_team_id UUID REFERENCES teams(id) ON DELETE SET NULL,
    approver_id UUID REFERENCES users(id) ON DELETE SET NULL,
    due_date DATE,
    status task_status NOT NULL DEFAULT 'DIRECTED',
    copied_from_task_id UUID REFERENCES tasks(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_task_assignee CHECK (
        (assignee_type = 'USER' AND assignee_user_id IS NOT NULL AND assignee_team_id IS NULL)
        OR (assignee_type = 'TEAM' AND assignee_user_id IS NULL AND assignee_team_id IS NOT NULL)
    )
);

CREATE TABLE task_completions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    comment TEXT NOT NULL,
    requested_by UUID NOT NULL REFERENCES users(id),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    approved_at TIMESTAMPTZ,
    rejection_reason TEXT
);

CREATE TABLE task_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE task_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL,
    content_type VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE task_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    title VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE linked_meetings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    linked_meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_not_self_link CHECK (meeting_id <> linked_meeting_id),
    UNIQUE (meeting_id, linked_meeting_id)
);

CREATE TABLE meeting_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    original_content TEXT,
    before_summary TEXT,
    after_summary TEXT,
    change_summary TEXT,
    modified_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (meeting_id, version_number)
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type notification_type NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    target_type VARCHAR(50),
    target_id UUID,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_team_id ON users(team_id);
CREATE INDEX idx_users_manager_id ON users(manager_id);
CREATE INDEX idx_meetings_organizer_id ON meetings(organizer_id);
CREATE INDEX idx_meetings_status ON meetings(status);
CREATE INDEX idx_meetings_scheduled_start_at ON meetings(scheduled_start_at);
CREATE INDEX idx_meetings_location_id ON meetings(location_id);
CREATE INDEX idx_meetings_status_scheduled_start ON meetings(status, scheduled_start_at);
CREATE INDEX idx_meetings_deleted_at ON meetings(deleted_at);
CREATE INDEX idx_meeting_participants_user_id ON meeting_participants(user_id);
CREATE INDEX idx_audio_files_meeting_id ON audio_files(meeting_id);
CREATE INDEX idx_speaker_mappings_meeting_id ON speaker_mappings(meeting_id);
CREATE INDEX idx_processing_jobs_status_retry ON ai_processing_jobs(status, next_retry_at);
CREATE INDEX idx_tasks_meeting_id ON tasks(meeting_id);
CREATE INDEX idx_tasks_assignee_user_id ON tasks(assignee_user_id);
CREATE INDEX idx_tasks_assignee_team_id ON tasks(assignee_team_id);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_due_date ON tasks(due_date);
CREATE INDEX idx_task_comments_task_id_created_at ON task_comments(task_id, created_at);
CREATE INDEX idx_linked_meetings_linked_id ON linked_meetings(linked_meeting_id);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, read_at);

-- 100일 보관 대상 조회용. 실제 영구 삭제는 애플리케이션 스케줄러가 수행한다.
CREATE INDEX idx_meetings_retention_cleanup
    ON meetings(deleted_at, retention_policy)
    WHERE deleted_at IS NOT NULL AND retention_policy = 'NORMAL';
