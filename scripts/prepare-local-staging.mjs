// Creates an isolated local Supabase workspace. Never links to a hosted project.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const target = path.join(root, '.local/staging/supabase');
fs.mkdirSync(path.join(target, 'migrations'), { recursive: true });
const config = `project_id = "studymind-local-staging"
[api]
enabled = true
port = 54321
schemas = ["public", "graphql_public"]
extra_search_path = ["public", "extensions"]
max_rows = 1000
[db]
port = 54322
shadow_port = 54320
major_version = 17
[db.seed]
enabled = false
[studio]
enabled = true
port = 54323
api_url = "http://127.0.0.1"
[local_smtp]
enabled = true
port = 54324
[auth]
enabled = true
site_url = "http://127.0.0.1:3000"
additional_redirect_urls = ["com.studymind.nativeapp://auth/callback"]
enable_signup = true
minimum_password_length = 8
[auth.email]
enable_signup = true
enable_confirmations = false
[storage]
enabled = true
file_size_limit = "50MiB"
[analytics]
enabled = false
[edge_runtime]
enabled = true
policy = "per_worker"
inspector_port = 8083
[functions.studymind-api]
verify_jwt = false
[functions.studymind-billing]
verify_jwt = false
`;
fs.writeFileSync(path.join(target, 'config.toml'), config);
// An empty local database needs this table before the owner-scoped migration.
fs.copyFileSync(path.join(root, 'docs/sql/staging-bootstrap.sql'), path.join(target, 'migrations/000_staging_bootstrap.sql'));
for (const name of ['001_lesson_ownership.sql', '002_studymind_pro.sql',
  '004_native_storage_pipeline.sql', '005_blockchain_rewards.sql',
  '006_reward_question_bank.sql', '007_reward_admin.sql', '008_reward_sepolia.sql', '009_lesson_reward_evidence.sql']) {
  fs.copyFileSync(path.join(root, 'supabase/migrations', name), path.join(target, 'migrations', name));
}
// Deliberately exclude 003: it grants admin to a fixed production email.
fs.cpSync(path.join(root, 'supabase/functions'), path.join(target, 'functions'), { recursive: true });
const envFile = path.join(target, 'functions/.env');
if (!fs.existsSync(envFile)) fs.writeFileSync(envFile, 'REWARDS_ENABLED=false\nREWARDS_CONFIRMATIONS=64\n');
console.log('Prepared .local/staging (local only; rewards disabled until Amoy is configured).');
