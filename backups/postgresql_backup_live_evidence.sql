--
-- PostgreSQL database dump
--

\restrict jCJQleQ5TrpWljJn3esi6PbltQd8rBUM7twTsGhmLKJygChmOih0Q1gsfeHgia8

-- Dumped from database version 15.19
-- Dumped by pg_dump version 15.19

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

ALTER TABLE IF EXISTS ONLY public.user_roles DROP CONSTRAINT IF EXISTS fk_user_roles_user;
ALTER TABLE IF EXISTS ONLY public.user_roles DROP CONSTRAINT IF EXISTS fk_user_roles_role;
ALTER TABLE IF EXISTS ONLY public.transaction_reviews DROP CONSTRAINT IF EXISTS fk_tx_review_reviewer;
ALTER TABLE IF EXISTS ONLY public.transaction_reviews DROP CONSTRAINT IF EXISTS fk_tx_review_employee;
ALTER TABLE IF EXISTS ONLY public.transaction_reviews DROP CONSTRAINT IF EXISTS fk_tx_review_actor;
ALTER TABLE IF EXISTS ONLY public.leave_request_signoffs DROP CONSTRAINT IF EXISTS fk_signoff_leave_request;
ALTER TABLE IF EXISTS ONLY public.leave_request_signoffs DROP CONSTRAINT IF EXISTS fk_signoff_approver;
ALTER TABLE IF EXISTS ONLY public.security_events DROP CONSTRAINT IF EXISTS fk_sec_event_user;
ALTER TABLE IF EXISTS ONLY public.security_events DROP CONSTRAINT IF EXISTS fk_sec_event_resolved_by;
ALTER TABLE IF EXISTS ONLY public.role_permissions DROP CONSTRAINT IF EXISTS fk_role_permissions_role;
ALTER TABLE IF EXISTS ONLY public.role_permissions DROP CONSTRAINT IF EXISTS fk_role_permissions_permission;
ALTER TABLE IF EXISTS ONLY public.refresh_tokens DROP CONSTRAINT IF EXISTS fk_refresh_tokens_user;
ALTER TABLE IF EXISTS ONLY public.login_history DROP CONSTRAINT IF EXISTS fk_login_history_user;
ALTER TABLE IF EXISTS ONLY public.leave_requests DROP CONSTRAINT IF EXISTS fk_leave_request_leave_type;
ALTER TABLE IF EXISTS ONLY public.leave_requests DROP CONSTRAINT IF EXISTS fk_leave_request_employee;
ALTER TABLE IF EXISTS ONLY public.leave_requests DROP CONSTRAINT IF EXISTS fk_leave_req_mgr_user;
ALTER TABLE IF EXISTS ONLY public.leave_requests DROP CONSTRAINT IF EXISTS fk_leave_req_hr_user;
ALTER TABLE IF EXISTS ONLY public.leave_balances DROP CONSTRAINT IF EXISTS fk_leave_balance_leave_type;
ALTER TABLE IF EXISTS ONLY public.leave_balances DROP CONSTRAINT IF EXISTS fk_leave_balance_employee;
ALTER TABLE IF EXISTS ONLY public.employees DROP CONSTRAINT IF EXISTS fk_employee_user;
ALTER TABLE IF EXISTS ONLY public.employees DROP CONSTRAINT IF EXISTS fk_employee_manager;
ALTER TABLE IF EXISTS ONLY public.employees DROP CONSTRAINT IF EXISTS fk_employee_department;
ALTER TABLE IF EXISTS ONLY public.departments DROP CONSTRAINT IF EXISTS fk_department_manager;
ALTER TABLE IF EXISTS ONLY public.authorization_audit_logs DROP CONSTRAINT IF EXISTS fk_authz_audit_user;
ALTER TABLE IF EXISTS ONLY public.audit_logs DROP CONSTRAINT IF EXISTS fk_audit_logs_user;
DROP INDEX IF EXISTS public.idx_tx_review_type;
DROP INDEX IF EXISTS public.idx_tx_review_status;
DROP INDEX IF EXISTS public.idx_tx_review_risk_level;
DROP INDEX IF EXISTS public.idx_tx_review_reviewer_id;
DROP INDEX IF EXISTS public.idx_tx_review_emp_id;
DROP INDEX IF EXISTS public.idx_tx_review_created_at;
DROP INDEX IF EXISTS public.idx_tx_review_actor_id;
DROP INDEX IF EXISTS public.idx_signoff_leave_request;
DROP INDEX IF EXISTS public.idx_signoff_approver;
DROP INDEX IF EXISTS public.idx_sec_event_user_id;
DROP INDEX IF EXISTS public.idx_sec_event_type;
DROP INDEX IF EXISTS public.idx_sec_event_threat_score;
DROP INDEX IF EXISTS public.idx_sec_event_status;
DROP INDEX IF EXISTS public.idx_sec_event_severity;
DROP INDEX IF EXISTS public.idx_sec_event_ip;
DROP INDEX IF EXISTS public.idx_sec_event_email;
DROP INDEX IF EXISTS public.idx_sec_event_created_at;
DROP INDEX IF EXISTS public.idx_login_history_user_id;
DROP INDEX IF EXISTS public.idx_login_history_status;
DROP INDEX IF EXISTS public.idx_login_history_login_time;
DROP INDEX IF EXISTS public.idx_login_history_email;
DROP INDEX IF EXISTS public.idx_leave_requests_status;
DROP INDEX IF EXISTS public.idx_leave_requests_employee_id;
DROP INDEX IF EXISTS public.idx_idempotency_expires_at;
DROP INDEX IF EXISTS public.idx_authz_audit_user_id;
DROP INDEX IF EXISTS public.idx_authz_audit_timestamp;
DROP INDEX IF EXISTS public.idx_authz_audit_email;
DROP INDEX IF EXISTS public.idx_authz_audit_decision;
DROP INDEX IF EXISTS public.idx_authz_audit_action;
DROP INDEX IF EXISTS public.idx_audit_logs_user_id;
DROP INDEX IF EXISTS public.idx_audit_logs_timestamp;
DROP INDEX IF EXISTS public.idx_audit_logs_entity;
DROP INDEX IF EXISTS public.idx_audit_logs_action;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_pkey;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_email_key;
ALTER TABLE IF EXISTS ONLY public.leave_balances DROP CONSTRAINT IF EXISTS uq_leave_balance;
ALTER TABLE IF EXISTS ONLY public.idempotency_keys DROP CONSTRAINT IF EXISTS uk_idempotency_key_user;
ALTER TABLE IF EXISTS ONLY public.transaction_reviews DROP CONSTRAINT IF EXISTS transaction_reviews_pkey;
ALTER TABLE IF EXISTS ONLY public.security_events DROP CONSTRAINT IF EXISTS security_events_pkey;
ALTER TABLE IF EXISTS ONLY public.roles DROP CONSTRAINT IF EXISTS roles_pkey;
ALTER TABLE IF EXISTS ONLY public.roles DROP CONSTRAINT IF EXISTS roles_name_key;
ALTER TABLE IF EXISTS ONLY public.refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_pkey;
ALTER TABLE IF EXISTS ONLY public.user_roles DROP CONSTRAINT IF EXISTS pk_user_roles;
ALTER TABLE IF EXISTS ONLY public.role_permissions DROP CONSTRAINT IF EXISTS pk_role_permissions;
ALTER TABLE IF EXISTS ONLY public.permissions DROP CONSTRAINT IF EXISTS permissions_pkey;
ALTER TABLE IF EXISTS ONLY public.permissions DROP CONSTRAINT IF EXISTS permissions_name_key;
ALTER TABLE IF EXISTS ONLY public.login_history DROP CONSTRAINT IF EXISTS login_history_pkey;
ALTER TABLE IF EXISTS ONLY public.leave_types DROP CONSTRAINT IF EXISTS leave_types_pkey;
ALTER TABLE IF EXISTS ONLY public.leave_types DROP CONSTRAINT IF EXISTS leave_types_name_key;
ALTER TABLE IF EXISTS ONLY public.leave_requests DROP CONSTRAINT IF EXISTS leave_requests_pkey;
ALTER TABLE IF EXISTS ONLY public.leave_request_signoffs DROP CONSTRAINT IF EXISTS leave_request_signoffs_pkey;
ALTER TABLE IF EXISTS ONLY public.leave_balances DROP CONSTRAINT IF EXISTS leave_balances_pkey;
ALTER TABLE IF EXISTS ONLY public.idempotency_keys DROP CONSTRAINT IF EXISTS idempotency_keys_pkey;
ALTER TABLE IF EXISTS ONLY public.employees DROP CONSTRAINT IF EXISTS employees_user_id_key;
ALTER TABLE IF EXISTS ONLY public.employees DROP CONSTRAINT IF EXISTS employees_pkey;
ALTER TABLE IF EXISTS ONLY public.employees DROP CONSTRAINT IF EXISTS employees_employee_code_key;
ALTER TABLE IF EXISTS ONLY public.departments DROP CONSTRAINT IF EXISTS departments_pkey;
ALTER TABLE IF EXISTS ONLY public.departments DROP CONSTRAINT IF EXISTS departments_name_key;
ALTER TABLE IF EXISTS ONLY public.databasechangeloglock DROP CONSTRAINT IF EXISTS databasechangeloglock_pkey;
ALTER TABLE IF EXISTS ONLY public.authorization_audit_logs DROP CONSTRAINT IF EXISTS authorization_audit_logs_pkey;
ALTER TABLE IF EXISTS ONLY public.audit_logs DROP CONSTRAINT IF EXISTS audit_logs_pkey;
DROP TABLE IF EXISTS public.users;
DROP TABLE IF EXISTS public.user_roles;
DROP TABLE IF EXISTS public.transaction_reviews;
DROP TABLE IF EXISTS public.security_events;
DROP TABLE IF EXISTS public.roles;
DROP TABLE IF EXISTS public.role_permissions;
DROP TABLE IF EXISTS public.refresh_tokens;
DROP TABLE IF EXISTS public.permissions;
DROP TABLE IF EXISTS public.login_history;
DROP TABLE IF EXISTS public.leave_types;
DROP TABLE IF EXISTS public.leave_requests;
DROP TABLE IF EXISTS public.leave_request_signoffs;
DROP TABLE IF EXISTS public.leave_balances;
DROP TABLE IF EXISTS public.idempotency_keys;
DROP TABLE IF EXISTS public.employees;
DROP TABLE IF EXISTS public.departments;
DROP TABLE IF EXISTS public.databasechangeloglock;
DROP TABLE IF EXISTS public.databasechangelog;
DROP TABLE IF EXISTS public.authorization_audit_logs;
DROP TABLE IF EXISTS public.audit_logs;
SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    id bigint NOT NULL,
    user_id bigint,
    action character varying(255) NOT NULL,
    entity_type character varying(255) NOT NULL,
    entity_id bigint,
    old_value character varying(1000),
    new_value character varying(1000),
    ip_address character varying(255),
    "timestamp" timestamp without time zone NOT NULL
);


--
-- Name: audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.audit_logs ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: authorization_audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.authorization_audit_logs (
    id bigint NOT NULL,
    user_id bigint,
    email character varying(255) NOT NULL,
    action character varying(100) NOT NULL,
    resource character varying(255) NOT NULL,
    permission_required character varying(150),
    decision character varying(20) NOT NULL,
    reason character varying(500),
    ip_address character varying(255),
    "timestamp" timestamp without time zone NOT NULL
);


--
-- Name: authorization_audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.authorization_audit_logs ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.authorization_audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: databasechangelog; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.databasechangelog (
    id character varying(255) NOT NULL,
    author character varying(255) NOT NULL,
    filename character varying(255) NOT NULL,
    dateexecuted timestamp without time zone NOT NULL,
    orderexecuted integer NOT NULL,
    exectype character varying(10) NOT NULL,
    md5sum character varying(35),
    description character varying(255),
    comments character varying(255),
    tag character varying(255),
    liquibase character varying(20),
    contexts character varying(255),
    labels character varying(255),
    deployment_id character varying(10)
);


--
-- Name: databasechangeloglock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.databasechangeloglock (
    id integer NOT NULL,
    locked boolean NOT NULL,
    lockgranted timestamp without time zone,
    lockedby character varying(255)
);


--
-- Name: departments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.departments (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    manager_id bigint
);


--
-- Name: departments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.departments ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.departments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: employees; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employees (
    id bigint NOT NULL,
    phone character varying(255),
    department_id bigint,
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL,
    user_id bigint,
    employee_code character varying(255),
    manager_id bigint,
    status character varying(255),
    salary numeric(10,2)
);


--
-- Name: employees_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.employees ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.employees_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: idempotency_keys; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.idempotency_keys (
    id bigint NOT NULL,
    idempotency_key character varying(255) NOT NULL,
    user_email character varying(255) DEFAULT 'anonymous'::character varying NOT NULL,
    request_path character varying(255) NOT NULL,
    request_method character varying(10) NOT NULL,
    request_hash character varying(64) NOT NULL,
    status character varying(30) NOT NULL,
    response_status integer,
    response_body text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    content_type character varying(255),
    response_headers text
);


--
-- Name: idempotency_keys_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.idempotency_keys ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.idempotency_keys_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: leave_balances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leave_balances (
    id bigint NOT NULL,
    employee_id bigint NOT NULL,
    leave_type_id bigint NOT NULL,
    available integer NOT NULL,
    year integer NOT NULL,
    total integer NOT NULL,
    used integer NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: leave_balances_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.leave_balances ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.leave_balances_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: leave_request_signoffs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leave_request_signoffs (
    id bigint NOT NULL,
    leave_request_id bigint NOT NULL,
    approver_role character varying(30) NOT NULL,
    approver_id bigint NOT NULL,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    comments character varying(500),
    signed_at timestamp without time zone DEFAULT now()
);


--
-- Name: leave_request_signoffs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.leave_request_signoffs ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.leave_request_signoffs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: leave_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leave_requests (
    id bigint NOT NULL,
    employee_id bigint NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    reason character varying(255),
    status character varying(255) NOT NULL,
    leave_type_id bigint NOT NULL,
    applied_at timestamp without time zone,
    rejection_reason character varying(500),
    version bigint DEFAULT 0 NOT NULL,
    requires_dual_approval boolean DEFAULT false NOT NULL,
    manager_approval_status character varying(30) DEFAULT 'PENDING'::character varying,
    manager_approved_by bigint,
    manager_approved_at timestamp without time zone,
    hr_approval_status character varying(30) DEFAULT 'PENDING'::character varying,
    hr_approved_by bigint,
    hr_approved_at timestamp without time zone
);


--
-- Name: leave_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.leave_requests ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.leave_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: leave_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leave_types (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500),
    default_days integer NOT NULL,
    active boolean DEFAULT true NOT NULL
);


--
-- Name: leave_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.leave_types ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.leave_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: login_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.login_history (
    id bigint NOT NULL,
    user_id bigint,
    email character varying(255) NOT NULL,
    login_time timestamp without time zone NOT NULL,
    ip_address character varying(255),
    user_agent character varying(500),
    status character varying(50) NOT NULL,
    failure_reason character varying(255)
);


--
-- Name: login_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.login_history ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.login_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(255),
    active boolean DEFAULT true NOT NULL
);


--
-- Name: permissions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.permissions ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    token_hash character varying(255) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    revoked boolean DEFAULT false NOT NULL
);


--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.refresh_tokens ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.refresh_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id bigint NOT NULL,
    name character varying(255) NOT NULL
);


--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.roles ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: security_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.security_events (
    id bigint NOT NULL,
    event_type character varying(100) NOT NULL,
    severity character varying(20) NOT NULL,
    status character varying(30) NOT NULL,
    user_id bigint,
    email character varying(255),
    ip_address character varying(255),
    user_agent character varying(500),
    resource character varying(255),
    threat_score integer DEFAULT 0 NOT NULL,
    threat_reason character varying(500),
    resolution_notes character varying(2000),
    resolved_by_id bigint,
    resolved_at timestamp without time zone,
    details character varying(4000),
    created_at timestamp without time zone NOT NULL
);


--
-- Name: security_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.security_events ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.security_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: transaction_reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaction_reviews (
    id bigint NOT NULL,
    transaction_type character varying(50) NOT NULL,
    entity_type character varying(50) NOT NULL,
    entity_id bigint NOT NULL,
    employee_id bigint,
    actor_id bigint,
    actor_email character varying(255) NOT NULL,
    risk_level character varying(20) NOT NULL,
    risk_score integer NOT NULL,
    risk_reason character varying(500),
    review_status character varying(30) NOT NULL,
    reviewer_id bigint,
    reviewer_email character varying(255),
    review_notes character varying(2000),
    resolution character varying(100),
    details_json character varying(4000),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone,
    reviewed_at timestamp without time zone
);


--
-- Name: transaction_reviews_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.transaction_reviews ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.transaction_reviews_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    last_login_at timestamp without time zone,
    last_login_ip character varying(255),
    login_count bigint DEFAULT 0 NOT NULL,
    account_non_locked boolean DEFAULT true NOT NULL,
    failed_login_attempts integer DEFAULT 0 NOT NULL,
    lock_expires_at timestamp without time zone
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.users ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Data for Name: audit_logs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.audit_logs (id, user_id, action, entity_type, entity_id, old_value, new_value, ip_address, "timestamp") FROM stdin;
1	\N	UNAUTHORIZED_ACCESS	SECURITY_EVENT	\N	\N	Access denied to secured resource: SecurityContextHolderAwareRequestWrapper[ org.springframework.security.web.header.HeaderWriterFilter$HeaderWriterRequest@2efe211]	192.168.65.1	2026-11-19 15:48:53.897545
2	\N	UNAUTHORIZED_ACCESS	SECURITY_EVENT	\N	\N	GET /actuator/metrics	192.168.65.1	2026-11-19 15:48:53.949286
3	\N	UNAUTHORIZED_ACCESS	SECURITY_EVENT	\N	\N	Access denied to secured resource: SecurityContextHolderAwareRequestWrapper[ org.springframework.security.web.header.HeaderWriterFilter$HeaderWriterRequest@7495c6ac]	192.168.65.1	2026-11-19 15:48:54.050321
4	\N	UNAUTHORIZED_ACCESS	SECURITY_EVENT	\N	\N	GET /actuator/env	192.168.65.1	2026-11-19 15:48:54.066163
5	1	SUCCESSFUL_LOGIN	SECURITY_EVENT	\N	\N	Authentication successful	192.168.65.1	2026-11-19 15:48:57.490272
6	1	TOKEN_REFRESHED	SECURITY_EVENT	\N	\N	Token refreshed successfully	192.168.65.1	2026-11-19 15:48:58.666308
7	2	SUCCESSFUL_LOGIN	SECURITY_EVENT	\N	\N	Authentication successful	192.168.65.1	2026-11-19 15:48:59.05985
8	2	EMPLOYEE_CREATED	Employee	2	\N	Registered through API	192.168.65.1	2026-11-19 15:48:59.074105
9	1	LEAVE_TYPE_CREATED	LeaveType	1	\N	{"id":1,"name":"Validation Leave","description":"Automated Validation Quota","defaultDays":25,"active":true}	192.168.65.1	2026-11-19 15:49:02.323514
10	1	BALANCE_CREATED	LeaveBalance	1	\N	{"id":1,"employee":{"id":2,"firstName":"Validation","lastName":"User","employeeCode":"VAL_9915","email":"emp_9915@validation.internal","phone":"[REDACTED]","status":"ACTIVE","salary":null,"department":null,"managerId":null,"managerName":null},"leaveType":{"id":1,"name":"Validation Leave","description":"Automated Validation Quota","defaultDays":25,"active":true},"year":2026,"total":25,"used":0,"available":25}	192.168.65.1	2026-11-19 15:49:02.418191
11	2	LEAVE_APPLIED	LeaveRequest	1	\N	{"id":1,"employee":{"id":2,"firstName":"Validation","lastName":"User","employeeCode":"VAL_9915","email":"emp_9915@validation.internal","phone":"[REDACTED]","status":"ACTIVE","salary":null,"department":null,"managerId":null,"managerName":null},"leaveType":{"id":1,"name":"Validation Leave","description":"Automated Validation Quota","defaultDays":25,"active":true},"startDate":[2026,11,26],"endDate":[2026,11,30],"reason":"Automated Docker Deployment Verification","status":"PENDING","rejectionReason":null,"appliedAt":[2026,11,19,15,49,2,527752847],"requestedDays":3,"requiresDualApproval":false,"managerApprovalStatus":"PENDING","managerApprovedByEmail":null,"managerApprovedAt":null,"hrApprovalStatus":"PENDING","hrApprovedByEmail":null,"hrApprovedAt":null,"signoffs":[]}	192.168.65.1	2026-11-19 15:49:02.538595
12	1	LEAVE_APPROVED	LeaveRequest	1	\N	{"id":1,"employee":{"id":2,"firstName":"Validation","lastName":"User","employeeCode":"VAL_9915","email":"emp_9915@validation.internal","phone":"[REDACTED]","status":"ACTIVE","salary":null,"department":null,"managerId":null,"managerName":null},"leaveType":{"id":1,"name":"Validation Leave","description":"Automated Validation Quota","defaultDays":25,"active":true},"startDate":[2026,11,26],"endDate":[2026,11,30],"reason":"Automated Docker Deployment Verification","status":"APPROVED","rejectionReason":null,"appliedAt":[2026,11,19,15,49,2,527753000],"requestedDays":3,"requiresDualApproval":false,"managerApprovalStatus":"PENDING","managerApprovedByEmail":null,"managerApprovedAt":null,"hrApprovalStatus":"PENDING","hrApprovedByEmail":null,"hrApprovedAt":null,"signoffs":[]}	192.168.65.1	2026-11-19 15:49:02.650113
13	2	SUCCESSFUL_LOGIN	SECURITY_EVENT	\N	\N	Authentication successful	192.168.65.1	2026-11-19 15:49:15.810409
14	1	SUCCESSFUL_LOGIN	SECURITY_EVENT	\N	\N	Authentication successful	192.168.65.1	2026-11-19 15:49:55.442879
15	1	SUCCESSFUL_LOGIN	SECURITY_EVENT	\N	\N	Authentication successful	192.168.65.1	2026-11-19 15:50:06.037611
16	1	SUCCESSFUL_LOGIN	SECURITY_EVENT	\N	\N	Authentication successful	192.168.65.1	2026-11-19 15:50:23.928197
17	1	SUCCESSFUL_LOGIN	SECURITY_EVENT	\N	\N	Authentication successful	192.168.65.1	2026-11-19 15:51:13.502828
\.


--
-- Data for Name: authorization_audit_logs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.authorization_audit_logs (id, user_id, email, action, resource, permission_required, decision, reason, ip_address, "timestamp") FROM stdin;
1	\N	anonymousUser	ACCESS_DENIED	SecurityContextHolderAwareRequestWrapper[ org.springframework.security.web.header.HeaderWriterFilter$HeaderWriterRequest@2efe211]	SECURITY_RULE	DENIED	Access denied to secured resource	192.168.65.1	2026-11-19 15:48:53.945233
2	\N	anonymousUser	ENDPOINT_ACCESS	GET /actuator/metrics	AUTHENTICATION	DENIED	Authentication required: 401 Unauthorized	192.168.65.1	2026-11-19 15:48:53.955728
3	\N	anonymousUser	ACCESS_DENIED	SecurityContextHolderAwareRequestWrapper[ org.springframework.security.web.header.HeaderWriterFilter$HeaderWriterRequest@7495c6ac]	SECURITY_RULE	DENIED	Access denied to secured resource	192.168.65.1	2026-11-19 15:48:54.062869
4	\N	anonymousUser	ENDPOINT_ACCESS	GET /actuator/env	AUTHENTICATION	DENIED	Authentication required: 401 Unauthorized	192.168.65.1	2026-11-19 15:48:54.07376
5	1	admin@leavemanagement.com	LEAVE_APPROVAL_CHECK	LeaveRequest#1	LEAVE_APPROVE	GRANTED	Authorized via global administrative role	192.168.65.1	2026-11-19 15:49:02.622554
6	1	admin@leavemanagement.com	LEAVE_APPROVAL_CHECK	LeaveRequest#1	LEAVE_APPROVE	GRANTED	Authorized via global administrative role	192.168.65.1	2026-11-19 15:49:02.626482
7	2	emp_9915@validation.internal	EMPLOYEE_VIEW_CHECK	Employee#2	SELF	GRANTED	Authorized as self	192.168.65.1	2026-11-19 15:49:02.700358
8	2	emp_9915@validation.internal	LEAVE_VIEW_CHECK	LeaveRequest#1	SELF	GRANTED	Authorized as leave applicant	192.168.65.1	2026-11-19 15:49:15.996305
9	2	emp_9915@validation.internal	LEAVE_VIEW_CHECK	LeaveRequest#1	SELF	GRANTED	Authorized as leave applicant	192.168.65.1	2026-11-19 15:49:16.008773
10	2	emp_9915@validation.internal	EMPLOYEE_VIEW_CHECK	Employee#2	SELF	GRANTED	Authorized as self	192.168.65.1	2026-11-19 15:49:16.115276
\.


--
-- Data for Name: databasechangelog; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) FROM stdin;
1	antigravity	db/changelog/db.changelog-master.yaml	2026-11-19 15:48:29.924469	1	EXECUTED	9:dc860e06f46742c4531b7fe2f32b48df	createTable tableName=departments; createTable tableName=employees; createTable tableName=leave_requests		\N	5.0.3	\N	\N	5103307426
2	antigravity	db/changelog/db.changelog-master.yaml	2026-11-19 15:48:29.931811	2	EXECUTED	9:0a34d6a51c9f44647a12006319edf0af	dropColumn columnName=name, tableName=employees; addColumn tableName=employees		\N	5.0.3	\N	\N	5103307426
004-create-leave-types	mohith	db/changelog/changes/004-create-leave-types.yaml	2026-11-19 15:48:29.939601	3	EXECUTED	9:a05f92b08714abad593281eba65cccb0	createTable tableName=leave_types		\N	5.0.3	\N	\N	5103307426
005-create-leave-balances	mohith	db/changelog/changes/005-create-leave-balances.yaml	2026-11-19 15:48:29.950586	4	EXECUTED	9:397410d2ba547d8dd858d77bb70be9bf	createTable tableName=leave_balances; addForeignKeyConstraint baseTableName=leave_balances, constraintName=fk_leave_balance_employee, referencedTableName=employees; addForeignKeyConstraint baseTableName=leave_balances, constraintName=fk_leave_bala...		\N	5.0.3	\N	\N	5103307426
006-update-leave-requests	antigravity	db/changelog/changes/006-update-leave-requests.yaml	2026-11-19 15:48:29.956562	5	EXECUTED	9:cba10d8643267a1e253bff5bac4edca5	addColumn tableName=leave_requests; addForeignKeyConstraint baseTableName=leave_requests, constraintName=fk_leave_request_leave_type, referencedTableName=leave_types		\N	5.0.3	\N	\N	5103307426
7	antigravity	db/changelog/changes/007-add-active-to-leave-types.yaml	2026-11-19 15:48:29.961909	6	EXECUTED	9:2409f40d0f38ebae2761c8f95f2e0eba	addColumn tableName=leave_types		\N	5.0.3	\N	\N	5103307426
008-add-rejection-reason-to-leave-requests	antigravity	db/changelog/changes/008-add-rejection-reason-to-leave-requests.yaml	2026-11-19 15:48:29.965079	7	EXECUTED	9:054db8b274d68bca74bd6e71139122c3	addColumn tableName=leave_requests		\N	5.0.3	\N	\N	5103307426
009-add-missing-constraints	antigravity	db/changelog/changes/009-add-missing-constraints.yaml	2026-11-19 15:48:29.979205	8	EXECUTED	9:38370d7980226e2139d99cfde7e659e4	addNotNullConstraint columnName=first_name, tableName=employees; addNotNullConstraint columnName=last_name, tableName=employees; addNotNullConstraint columnName=start_date, tableName=leave_requests; addNotNullConstraint columnName=end_date, tableN...		\N	5.0.3	\N	\N	5103307426
010-setup-security-tables	antigravity	db/changelog/changes/010-setup-security-tables.yaml	2026-11-19 15:48:30.012554	9	EXECUTED	9:280861f2a74ae240e162c5a331c174c9	createTable tableName=users; createTable tableName=roles; createTable tableName=user_roles; addPrimaryKey constraintName=pk_user_roles, tableName=user_roles		\N	5.0.3	\N	\N	5103307426
010-insert-default-roles	antigravity	db/changelog/changes/010-setup-security-tables.yaml	2026-11-19 15:48:30.017587	10	EXECUTED	9:94821ba9c1f62d0b3889277d261d47e9	insert tableName=roles; insert tableName=roles; insert tableName=roles; insert tableName=roles		\N	5.0.3	\N	\N	5103307426
011-setup-tokens-and-audit	antigravity	db/changelog/changes/011-setup-tokens-and-audit.yaml	2026-11-19 15:48:30.030801	11	EXECUTED	9:c3ed267da7310e241a92d882daaba8e4	createTable tableName=refresh_tokens; createTable tableName=audit_logs		\N	5.0.3	\N	\N	5103307426
012-modify-employees-and-departments	antigravity	db/changelog/changes/012-modify-employees-and-constraints.yaml	2026-11-19 15:48:30.059591	12	EXECUTED	9:da5872660f0e35d8ba8b23f6c6aa3bc7	addColumn tableName=employees; addForeignKeyConstraint baseTableName=employees, constraintName=fk_employee_user, referencedTableName=users; addForeignKeyConstraint baseTableName=employees, constraintName=fk_employee_manager, referencedTableName=em...		\N	5.0.3	\N	\N	5103307426
013-add-version-columns	system	db/changelog/changes/013-add-version-columns.yaml	2026-11-19 15:48:30.06672	13	EXECUTED	9:6b9c058180a41ffa8557ffee4e2109d5	addColumn tableName=leave_requests; addColumn tableName=leave_balances		\N	5.0.3	\N	\N	5103307426
014-add-permissions	system	db/changelog/changes/014-add-permissions.yaml	2026-11-19 15:48:30.143084	14	EXECUTED	9:d4c0a6b21469efce25ae4aee4fb11fb6	createTable tableName=permissions; createTable tableName=role_permissions; addPrimaryKey constraintName=pk_role_permissions, tableName=role_permissions; insert tableName=permissions; insert tableName=permissions; insert tableName=permissions; inse...		\N	5.0.3	\N	\N	5103307426
015-modify-rejection-reason-length	antigravity	db/changelog/changes/015-modify-rejection-reason-length.yaml	2026-11-19 15:48:30.147224	15	EXECUTED	9:9e317637be01f3c3afbe33d8789378fe	modifyDataType columnName=rejection_reason, tableName=leave_requests		\N	5.0.3	\N	\N	5103307426
016-add-audit-log-indexes	system	db/changelog/changes/016-add-audit-log-indexes.yaml	2026-11-19 15:48:30.158829	16	EXECUTED	9:a4baefceb156dfe4f361a8ef8d8835b1	createIndex indexName=idx_audit_logs_timestamp, tableName=audit_logs; createIndex indexName=idx_audit_logs_action, tableName=audit_logs; createIndex indexName=idx_audit_logs_entity, tableName=audit_logs; createIndex indexName=idx_audit_logs_user_i...		\N	5.0.3	\N	\N	5103307426
017-add-login-tracking	antigravity	db/changelog/changes/017-add-login-tracking.yaml	2026-11-19 15:48:30.177798	17	EXECUTED	9:e60d22edccea6eaf801197b826a8f219	addColumn tableName=users; createTable tableName=login_history; createIndex indexName=idx_login_history_user_id, tableName=login_history; createIndex indexName=idx_login_history_email, tableName=login_history; createIndex indexName=idx_login_histo...		\N	5.0.3	\N	\N	5103307426
018-add-authorization-auditing	antigravity	db/changelog/changes/018-add-authorization-auditing.yaml	2026-11-19 15:48:30.228784	18	EXECUTED	9:b7df1035652d04bb78b95205d9faf63c	createTable tableName=authorization_audit_logs; createIndex indexName=idx_authz_audit_user_id, tableName=authorization_audit_logs; createIndex indexName=idx_authz_audit_email, tableName=authorization_audit_logs; createIndex indexName=idx_authz_aud...		\N	5.0.3	\N	\N	5103307426
019-add-transaction-review	antigravity	db/changelog/changes/019-add-transaction-review.yaml	2026-11-19 15:48:30.253107	19	EXECUTED	9:68344cbc9866bddefc43a0bcbf8ca5fc	createTable tableName=transaction_reviews; createIndex indexName=idx_tx_review_type, tableName=transaction_reviews; createIndex indexName=idx_tx_review_status, tableName=transaction_reviews; createIndex indexName=idx_tx_review_risk_level, tableNam...		\N	5.0.3	\N	\N	5103307426
020-add-security-events	antigravity	db/changelog/changes/020-add-security-events.yaml	2026-11-19 15:48:30.279894	20	EXECUTED	9:86f16def712f5273607901fc481840a8	createTable tableName=security_events; createIndex indexName=idx_sec_event_type, tableName=security_events; createIndex indexName=idx_sec_event_severity, tableName=security_events; createIndex indexName=idx_sec_event_status, tableName=security_eve...		\N	5.0.3	\N	\N	5103307426
021-add-security-management-permissions	antigravity	db/changelog/changes/021-add-security-management-permissions.yaml	2026-11-19 15:48:30.297941	21	EXECUTED	9:9eb9dec13a3c529a1d2498a76cf8afff	insert tableName=permissions; insert tableName=permissions; insert tableName=permissions; insert tableName=roles; sql		\N	5.0.3	\N	\N	5103307426
022-add-user-account-locking	antigravity	db/changelog/changes/022-add-user-account-locking.yaml	2026-11-19 15:48:30.315976	22	EXECUTED	9:cdd1ef7207602caa25bc5ab0d37dcb89	addColumn tableName=users; insert tableName=permissions; sql		\N	5.0.3	\N	\N	5103307426
023-add-multi-approver-signoff	antigravity	db/changelog/changes/023-add-multi-approver-signoff.yaml	2026-11-19 15:48:30.334686	23	EXECUTED	9:31694453abfff0fdf8c5aebb2d18cb41	addColumn tableName=leave_requests; createTable tableName=leave_request_signoffs; createIndex indexName=idx_signoff_leave_request, tableName=leave_request_signoffs; createIndex indexName=idx_signoff_approver, tableName=leave_request_signoffs		\N	5.0.3	\N	\N	5103307426
024-add-idempotency-keys	antigravity	db/changelog/changes/024-add-idempotency-keys.yaml	2026-11-19 15:48:30.345861	24	EXECUTED	9:05547ef01ea42f73f3b265dcf82a8067	createTable tableName=idempotency_keys; addUniqueConstraint constraintName=uk_idempotency_key_user, tableName=idempotency_keys; createIndex indexName=idx_idempotency_expires_at, tableName=idempotency_keys		\N	5.0.3	\N	\N	5103307426
025-add-idempotency-response-headers	antigravity	db/changelog/changes/025-add-idempotency-response-headers.yaml	2026-11-19 15:48:30.34997	25	EXECUTED	9:da9c0c526891701c7a53f6a3cce894b1	addColumn tableName=idempotency_keys		\N	5.0.3	\N	\N	5103307426
026-convert-bit-columns-to-boolean-postgresql	antigravity	db/changelog/changes/026-convert-bit-columns-to-boolean.yaml	2026-11-19 15:48:30.352867	26	EXECUTED	9:0a7ed49c904abc7110aa09324b49f106	sql		\N	5.0.3	\N	\N	5103307426
026-convert-leave-types-active-to-boolean-postgresql	antigravity	db/changelog/changes/026-convert-bit-columns-to-boolean.yaml	2026-11-19 15:48:30.365079	27	MARK_RAN	9:adb76642ad9a703eb4f4054fe2d19cee	sql		\N	5.0.3	\N	\N	5103307426
026-convert-users-active-to-boolean-postgresql	antigravity	db/changelog/changes/026-convert-bit-columns-to-boolean.yaml	2026-11-19 15:48:30.372511	28	MARK_RAN	9:b8557e0e50aae9d81b36b386305fcbe7	sql		\N	5.0.3	\N	\N	5103307426
026-convert-tokens-revoked-to-boolean-postgresql	antigravity	db/changelog/changes/026-convert-bit-columns-to-boolean.yaml	2026-11-19 15:48:30.37732	29	MARK_RAN	9:e06abd4fdc2c3c2e5e6ec4d6ae0b5668	sql		\N	5.0.3	\N	\N	5103307426
\.


--
-- Data for Name: databasechangeloglock; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.databasechangeloglock (id, locked, lockgranted, lockedby) FROM stdin;
1	f	\N	\N
\.


--
-- Data for Name: departments; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.departments (id, name, manager_id) FROM stdin;
\.


--
-- Data for Name: employees; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.employees (id, phone, department_id, first_name, last_name, user_id, employee_code, manager_id, status, salary) FROM stdin;
1	1234567890	\N	System	Administrator	1	ADM-001	\N	ACTIVE	\N
\.


--
-- Data for Name: idempotency_keys; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.idempotency_keys (id, idempotency_key, user_email, request_path, request_method, request_hash, status, response_status, response_body, created_at, expires_at, content_type, response_headers) FROM stdin;
\.


--
-- Data for Name: leave_balances; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.leave_balances (id, employee_id, leave_type_id, available, year, total, used, version) FROM stdin;
\.


--
-- Data for Name: leave_request_signoffs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.leave_request_signoffs (id, leave_request_id, approver_role, approver_id, status, comments, signed_at) FROM stdin;
\.


--
-- Data for Name: leave_requests; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.leave_requests (id, employee_id, start_date, end_date, reason, status, leave_type_id, applied_at, rejection_reason, version, requires_dual_approval, manager_approval_status, manager_approved_by, manager_approved_at, hr_approval_status, hr_approved_by, hr_approved_at) FROM stdin;
\.


--
-- Data for Name: leave_types; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.leave_types (id, name, description, default_days, active) FROM stdin;
\.


--
-- Data for Name: login_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.login_history (id, user_id, email, login_time, ip_address, user_agent, status, failure_reason) FROM stdin;
1	1	admin@leavemanagement.com	2026-11-19 15:48:57.519318	192.168.65.1	curl/8.21.0	SUCCESS	\N
2	2	emp_9915@validation.internal	2026-11-19 15:48:59.069324	192.168.65.1	curl/8.21.0	SUCCESS	\N
3	2	emp_9915@validation.internal	2026-11-19 15:49:15.85597	192.168.65.1	curl/8.21.0	SUCCESS	\N
4	1	admin@leavemanagement.com	2026-11-19 15:49:55.468688	192.168.65.1	Java-http-client/25.0.1	SUCCESS	\N
5	1	admin@leavemanagement.com	2026-11-19 15:50:06.063583	192.168.65.1	Java-http-client/25.0.1	SUCCESS	\N
6	1	admin@leavemanagement.com	2026-11-19 15:50:23.957494	192.168.65.1	Java-http-client/25.0.1	SUCCESS	\N
7	1	admin@leavemanagement.com	2026-11-19 15:51:13.540991	192.168.65.1	Python-urllib/3.14	SUCCESS	\N
\.


--
-- Data for Name: permissions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.permissions (id, name, description, active) FROM stdin;
1	EMPLOYEE_VIEW	View employee profiles	t
2	EMPLOYEE_CREATE	Create new employees	t
3	EMPLOYEE_UPDATE	Update employee profiles	t
4	EMPLOYEE_DEACTIVATE	Deactivate employees	t
5	DEPARTMENT_VIEW	View departments	t
6	DEPARTMENT_MANAGE	Manage departments	t
7	LEAVE_VIEW_SELF	View own leave requests	t
8	LEAVE_VIEW_TEAM	View team leave requests	t
9	LEAVE_VIEW_ALL	View all leave requests	t
10	LEAVE_CREATE	Create leave request	t
11	LEAVE_APPROVE	Approve leave request	t
12	LEAVE_REJECT	Reject leave request	t
13	LEAVE_CANCEL	Cancel leave request	t
14	LEAVE_BALANCE_VIEW	View leave balances	t
15	LEAVE_BALANCE_MANAGE	Manage leave balances	t
16	LEAVE_TYPE_MANAGE	Manage leave types	t
17	ROLE_PERMISSION_MANAGE	Manage role permissions	t
18	SECURITY_EVENT_MANAGE	Triage, investigate, resolve, and manage security incidents	t
19	AUDIT_LOG_VIEW	View security audit logs and incident history	t
20	ADMIN_VIEW	View administrative dashboards and security posture	t
21	USER_LOCK_MANAGE	Manually lock and unlock user accounts	t
\.


--
-- Data for Name: refresh_tokens; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.refresh_tokens (id, user_id, token_hash, expires_at, revoked) FROM stdin;
2	1	c7eb83a146e13d43e7c27c9114861ada2502812829b58e8bb8685c4183cb3144	2026-11-26 15:48:58.664059	f
5	1	8c00253182f6faabd05ca5ca0d5c3545511ceb081c472e187cbc86a0fad1770f	2026-11-26 15:49:55.479893	f
6	1	b732b176a5626fe6f539e3cd3468762d37595d7dbbf25d1f1920ed46cbcd86f8	2026-11-26 15:50:06.081921	f
7	1	b203ceb5979bde7abb242d496197d5f0f2a2c2d03537db3a3f1818298d89bae5	2026-11-26 15:50:23.968489	f
8	1	007799b10e4014ad0638c4d9f95bbb33538d714e6015872bc11e1a21db01450a	2026-11-26 15:51:13.552345	f
\.


--
-- Data for Name: role_permissions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.role_permissions (role_id, permission_id) FROM stdin;
1	1
1	2
1	3
1	4
1	5
1	6
1	7
1	8
1	9
1	10
1	11
1	12
1	13
1	14
1	15
1	16
1	17
2	1
2	2
2	3
2	4
2	5
2	6
2	9
2	10
2	11
2	12
2	13
2	14
2	15
2	16
3	1
3	5
3	8
3	11
3	12
4	7
4	10
4	13
4	14
1	18
5	18
1	19
5	19
1	20
5	20
1	21
5	21
\.


--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.roles (id, name) FROM stdin;
1	ADMIN
2	HR
3	MANAGER
4	EMPLOYEE
5	SECURITY_OFFICER
\.


--
-- Data for Name: security_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.security_events (id, event_type, severity, status, user_id, email, ip_address, user_agent, resource, threat_score, threat_reason, resolution_notes, resolved_by_id, resolved_at, details, created_at) FROM stdin;
1	UNAUTHORIZED_ACCESS	HIGH	OPEN	\N	anonymousUser	192.168.65.1	curl/8.21.0	\N	70	Insufficient privilege or unauthenticated access attempt on protected endpoint	\N	\N	\N	Access denied to secured resource: SecurityContextHolderAwareRequestWrapper[ org.springframework.security.web.header.HeaderWriterFilter$HeaderWriterRequest@2efe211]	2026-11-19 15:48:53.917981
2	UNAUTHORIZED_ACCESS	HIGH	OPEN	\N	\N	192.168.65.1	curl/8.21.0	\N	70	Insufficient privilege or unauthenticated access attempt on protected endpoint	\N	\N	\N	GET /actuator/metrics	2026-11-19 15:48:53.950596
3	UNAUTHORIZED_ACCESS	HIGH	OPEN	\N	anonymousUser	192.168.65.1	curl/8.21.0	\N	70	Insufficient privilege or unauthenticated access attempt on protected endpoint	\N	\N	\N	Access denied to secured resource: SecurityContextHolderAwareRequestWrapper[ org.springframework.security.web.header.HeaderWriterFilter$HeaderWriterRequest@7495c6ac]	2026-11-19 15:48:54.053347
4	REPEATED_UNAUTHORIZED_ACCESS	HIGH	OPEN	\N	anonymousUser	192.168.65.1	curl/8.21.0	\N	85	Automated threat detection: 3 unauthorized access attempts within 10 minutes.	\N	\N	\N	Source IP: 192.168.65.1, Target Resource: null	2026-11-19 15:48:54.058266
5	UNAUTHORIZED_ACCESS	HIGH	OPEN	\N	\N	192.168.65.1	curl/8.21.0	\N	70	Insufficient privilege or unauthenticated access attempt on protected endpoint	\N	\N	\N	GET /actuator/env	2026-11-19 15:48:54.068035
6	SUCCESSFUL_LOGIN	LOW	RESOLVED	1	admin@leavemanagement.com	192.168.65.1	curl/8.21.0	\N	0	\N	System auto-resolved routine authenticated activity	\N	\N	Authentication successful	2026-11-19 15:48:57.497491
7	TOKEN_REFRESHED	LOW	RESOLVED	1	admin@leavemanagement.com	192.168.65.1	curl/8.21.0	\N	0	\N	System auto-resolved routine authenticated activity	\N	\N	Token refreshed successfully	2026-11-19 15:48:58.675019
8	SUCCESSFUL_LOGIN	LOW	RESOLVED	2	emp_9915@validation.internal	192.168.65.1	curl/8.21.0	\N	0	\N	System auto-resolved routine authenticated activity	\N	\N	Authentication successful	2026-11-19 15:48:59.062535
9	SUCCESSFUL_LOGIN	LOW	RESOLVED	2	emp_9915@validation.internal	192.168.65.1	curl/8.21.0	\N	0	\N	System auto-resolved routine authenticated activity	\N	\N	Authentication successful	2026-11-19 15:49:15.837355
10	SUCCESSFUL_LOGIN	LOW	RESOLVED	1	admin@leavemanagement.com	192.168.65.1	Java-http-client/25.0.1	\N	0	\N	System auto-resolved routine authenticated activity	\N	\N	Authentication successful	2026-11-19 15:49:55.453628
11	SUCCESSFUL_LOGIN	LOW	RESOLVED	1	admin@leavemanagement.com	192.168.65.1	Java-http-client/25.0.1	\N	0	\N	System auto-resolved routine authenticated activity	\N	\N	Authentication successful	2026-11-19 15:50:06.043345
12	SUCCESSFUL_LOGIN	LOW	RESOLVED	1	admin@leavemanagement.com	192.168.65.1	Java-http-client/25.0.1	\N	0	\N	System auto-resolved routine authenticated activity	\N	\N	Authentication successful	2026-11-19 15:50:23.944033
13	SUCCESSFUL_LOGIN	LOW	RESOLVED	1	admin@leavemanagement.com	192.168.65.1	Python-urllib/3.14	\N	0	\N	System auto-resolved routine authenticated activity	\N	\N	Authentication successful	2026-11-19 15:51:13.520304
\.


--
-- Data for Name: transaction_reviews; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.transaction_reviews (id, transaction_type, entity_type, entity_id, employee_id, actor_id, actor_email, risk_level, risk_score, risk_reason, review_status, reviewer_id, reviewer_email, review_notes, resolution, details_json, created_at, updated_at, reviewed_at) FROM stdin;
\.


--
-- Data for Name: user_roles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_roles (user_id, role_id) FROM stdin;
1	4
1	1
1	2
1	3
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.users (id, email, password, active, last_login_at, last_login_ip, login_count, account_non_locked, failed_login_attempts, lock_expires_at) FROM stdin;
2	emp_9915@validation.internal	$2a$10$loAZPTuXLf2WYK0w3CUVzeHtwK9dfbCJhoHqrq82Y4vKHYuPYN3kO	t	2026-11-19 15:49:15.85276	192.168.65.1	2	t	0	\N
1	admin@leavemanagement.com	$2a$10$B2MBjZSEf/uaTDtYYYzDdOxwQqX19u3C9d3mAjRjAnfXGt/u2CIV6	t	2026-11-19 15:51:13.540143	192.168.65.1	5	t	0	\N
\.


--
-- Name: audit_logs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.audit_logs_id_seq', 17, true);


--
-- Name: authorization_audit_logs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.authorization_audit_logs_id_seq', 10, true);


--
-- Name: departments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.departments_id_seq', 1, false);


--
-- Name: employees_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.employees_id_seq', 2, true);


--
-- Name: idempotency_keys_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.idempotency_keys_id_seq', 1, false);


--
-- Name: leave_balances_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_balances_id_seq', 1, true);


--
-- Name: leave_request_signoffs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_request_signoffs_id_seq', 1, false);


--
-- Name: leave_requests_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_requests_id_seq', 1, true);


--
-- Name: leave_types_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.leave_types_id_seq', 1, true);


--
-- Name: login_history_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.login_history_id_seq', 7, true);


--
-- Name: permissions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.permissions_id_seq', 21, true);


--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.refresh_tokens_id_seq', 8, true);


--
-- Name: roles_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.roles_id_seq', 5, true);


--
-- Name: security_events_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.security_events_id_seq', 13, true);


--
-- Name: transaction_reviews_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.transaction_reviews_id_seq', 3, true);


--
-- Name: users_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.users_id_seq', 2, true);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: authorization_audit_logs authorization_audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.authorization_audit_logs
    ADD CONSTRAINT authorization_audit_logs_pkey PRIMARY KEY (id);


--
-- Name: databasechangeloglock databasechangeloglock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.databasechangeloglock
    ADD CONSTRAINT databasechangeloglock_pkey PRIMARY KEY (id);


--
-- Name: departments departments_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT departments_name_key UNIQUE (name);


--
-- Name: departments departments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT departments_pkey PRIMARY KEY (id);


--
-- Name: employees employees_employee_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT employees_employee_code_key UNIQUE (employee_code);


--
-- Name: employees employees_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT employees_pkey PRIMARY KEY (id);


--
-- Name: employees employees_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT employees_user_id_key UNIQUE (user_id);


--
-- Name: idempotency_keys idempotency_keys_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_keys
    ADD CONSTRAINT idempotency_keys_pkey PRIMARY KEY (id);


--
-- Name: leave_balances leave_balances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_balances
    ADD CONSTRAINT leave_balances_pkey PRIMARY KEY (id);


--
-- Name: leave_request_signoffs leave_request_signoffs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_request_signoffs
    ADD CONSTRAINT leave_request_signoffs_pkey PRIMARY KEY (id);


--
-- Name: leave_requests leave_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT leave_requests_pkey PRIMARY KEY (id);


--
-- Name: leave_types leave_types_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_types
    ADD CONSTRAINT leave_types_name_key UNIQUE (name);


--
-- Name: leave_types leave_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_types
    ADD CONSTRAINT leave_types_pkey PRIMARY KEY (id);


--
-- Name: login_history login_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_history
    ADD CONSTRAINT login_history_pkey PRIMARY KEY (id);


--
-- Name: permissions permissions_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_name_key UNIQUE (name);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);


--
-- Name: role_permissions pk_role_permissions; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id);


--
-- Name: user_roles pk_user_roles; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: roles roles_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_name_key UNIQUE (name);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: security_events security_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_events
    ADD CONSTRAINT security_events_pkey PRIMARY KEY (id);


--
-- Name: transaction_reviews transaction_reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_reviews
    ADD CONSTRAINT transaction_reviews_pkey PRIMARY KEY (id);


--
-- Name: idempotency_keys uk_idempotency_key_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_keys
    ADD CONSTRAINT uk_idempotency_key_user UNIQUE (idempotency_key, user_email);


--
-- Name: leave_balances uq_leave_balance; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_balances
    ADD CONSTRAINT uq_leave_balance UNIQUE (employee_id, leave_type_id, year);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_audit_logs_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_action ON public.audit_logs USING btree (action);


--
-- Name: idx_audit_logs_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_entity ON public.audit_logs USING btree (entity_type, entity_id);


--
-- Name: idx_audit_logs_timestamp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_timestamp ON public.audit_logs USING btree ("timestamp");


--
-- Name: idx_audit_logs_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_user_id ON public.audit_logs USING btree (user_id);


--
-- Name: idx_authz_audit_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_authz_audit_action ON public.authorization_audit_logs USING btree (action);


--
-- Name: idx_authz_audit_decision; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_authz_audit_decision ON public.authorization_audit_logs USING btree (decision);


--
-- Name: idx_authz_audit_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_authz_audit_email ON public.authorization_audit_logs USING btree (email);


--
-- Name: idx_authz_audit_timestamp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_authz_audit_timestamp ON public.authorization_audit_logs USING btree ("timestamp");


--
-- Name: idx_authz_audit_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_authz_audit_user_id ON public.authorization_audit_logs USING btree (user_id);


--
-- Name: idx_idempotency_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_idempotency_expires_at ON public.idempotency_keys USING btree (expires_at);


--
-- Name: idx_leave_requests_employee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leave_requests_employee_id ON public.leave_requests USING btree (employee_id);


--
-- Name: idx_leave_requests_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leave_requests_status ON public.leave_requests USING btree (status);


--
-- Name: idx_login_history_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_login_history_email ON public.login_history USING btree (email);


--
-- Name: idx_login_history_login_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_login_history_login_time ON public.login_history USING btree (login_time);


--
-- Name: idx_login_history_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_login_history_status ON public.login_history USING btree (status);


--
-- Name: idx_login_history_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_login_history_user_id ON public.login_history USING btree (user_id);


--
-- Name: idx_sec_event_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sec_event_created_at ON public.security_events USING btree (created_at);


--
-- Name: idx_sec_event_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sec_event_email ON public.security_events USING btree (email);


--
-- Name: idx_sec_event_ip; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sec_event_ip ON public.security_events USING btree (ip_address);


--
-- Name: idx_sec_event_severity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sec_event_severity ON public.security_events USING btree (severity);


--
-- Name: idx_sec_event_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sec_event_status ON public.security_events USING btree (status);


--
-- Name: idx_sec_event_threat_score; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sec_event_threat_score ON public.security_events USING btree (threat_score);


--
-- Name: idx_sec_event_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sec_event_type ON public.security_events USING btree (event_type);


--
-- Name: idx_sec_event_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sec_event_user_id ON public.security_events USING btree (user_id);


--
-- Name: idx_signoff_approver; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_signoff_approver ON public.leave_request_signoffs USING btree (approver_id);


--
-- Name: idx_signoff_leave_request; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_signoff_leave_request ON public.leave_request_signoffs USING btree (leave_request_id);


--
-- Name: idx_tx_review_actor_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tx_review_actor_id ON public.transaction_reviews USING btree (actor_id);


--
-- Name: idx_tx_review_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tx_review_created_at ON public.transaction_reviews USING btree (created_at);


--
-- Name: idx_tx_review_emp_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tx_review_emp_id ON public.transaction_reviews USING btree (employee_id);


--
-- Name: idx_tx_review_reviewer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tx_review_reviewer_id ON public.transaction_reviews USING btree (reviewer_id);


--
-- Name: idx_tx_review_risk_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tx_review_risk_level ON public.transaction_reviews USING btree (risk_level);


--
-- Name: idx_tx_review_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tx_review_status ON public.transaction_reviews USING btree (review_status);


--
-- Name: idx_tx_review_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tx_review_type ON public.transaction_reviews USING btree (transaction_type);


--
-- Name: audit_logs fk_audit_logs_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: authorization_audit_logs fk_authz_audit_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.authorization_audit_logs
    ADD CONSTRAINT fk_authz_audit_user FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: departments fk_department_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT fk_department_manager FOREIGN KEY (manager_id) REFERENCES public.employees(id);


--
-- Name: employees fk_employee_department; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT fk_employee_department FOREIGN KEY (department_id) REFERENCES public.departments(id);


--
-- Name: employees fk_employee_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT fk_employee_manager FOREIGN KEY (manager_id) REFERENCES public.employees(id);


--
-- Name: employees fk_employee_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT fk_employee_user FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: leave_balances fk_leave_balance_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_balances
    ADD CONSTRAINT fk_leave_balance_employee FOREIGN KEY (employee_id) REFERENCES public.employees(id);


--
-- Name: leave_balances fk_leave_balance_leave_type; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_balances
    ADD CONSTRAINT fk_leave_balance_leave_type FOREIGN KEY (leave_type_id) REFERENCES public.leave_types(id);


--
-- Name: leave_requests fk_leave_req_hr_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT fk_leave_req_hr_user FOREIGN KEY (hr_approved_by) REFERENCES public.users(id);


--
-- Name: leave_requests fk_leave_req_mgr_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT fk_leave_req_mgr_user FOREIGN KEY (manager_approved_by) REFERENCES public.users(id);


--
-- Name: leave_requests fk_leave_request_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT fk_leave_request_employee FOREIGN KEY (employee_id) REFERENCES public.employees(id);


--
-- Name: leave_requests fk_leave_request_leave_type; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT fk_leave_request_leave_type FOREIGN KEY (leave_type_id) REFERENCES public.leave_types(id);


--
-- Name: login_history fk_login_history_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_history
    ADD CONSTRAINT fk_login_history_user FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: refresh_tokens fk_refresh_tokens_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: role_permissions fk_role_permissions_permission; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES public.permissions(id);


--
-- Name: role_permissions fk_role_permissions_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: security_events fk_sec_event_resolved_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_events
    ADD CONSTRAINT fk_sec_event_resolved_by FOREIGN KEY (resolved_by_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: security_events fk_sec_event_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_events
    ADD CONSTRAINT fk_sec_event_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: leave_request_signoffs fk_signoff_approver; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_request_signoffs
    ADD CONSTRAINT fk_signoff_approver FOREIGN KEY (approver_id) REFERENCES public.users(id);


--
-- Name: leave_request_signoffs fk_signoff_leave_request; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_request_signoffs
    ADD CONSTRAINT fk_signoff_leave_request FOREIGN KEY (leave_request_id) REFERENCES public.leave_requests(id) ON DELETE CASCADE;


--
-- Name: transaction_reviews fk_tx_review_actor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_reviews
    ADD CONSTRAINT fk_tx_review_actor FOREIGN KEY (actor_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: transaction_reviews fk_tx_review_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_reviews
    ADD CONSTRAINT fk_tx_review_employee FOREIGN KEY (employee_id) REFERENCES public.employees(id) ON DELETE CASCADE;


--
-- Name: transaction_reviews fk_tx_review_reviewer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_reviews
    ADD CONSTRAINT fk_tx_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: user_roles fk_user_roles_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: user_roles fk_user_roles_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- PostgreSQL database dump complete
--

\unrestrict jCJQleQ5TrpWljJn3esi6PbltQd8rBUM7twTsGhmLKJygChmOih0Q1gsfeHgia8

