# Hướng dẫn bật StudyCoin B trên Supabase staging và Polygon Amoy

Áp dụng cho dự án tại `C:\Projects\AI-hoc-tap`. Các lệnh bên dưới chạy trong **cùng một cửa sổ PowerShell** trừ khi có chỉ dẫn khác. Đây là hướng dẫn để bạn thực hiện; chưa có project, ví hay contract nào được tạo trên mạng bởi tài liệu này.

Mục tiêu: project Supabase riêng → database → ví/gas thử nghiệm → contract → backend → worker → APK staging.

## 1. Tạo Supabase staging

1. Vào [Supabase Dashboard](https://supabase.com/dashboard), chọn **New project**.
2. Đặt tên `studymind-staging`, chọn organization/region phù hợp và lưu database password trong trình quản lý mật khẩu. Nếu organization đã hết suất project, xử lý giới hạn đó trước; không xóa project đang chạy để lấy chỗ.
3. Khi project sẵn sàng, lấy **Project URL**, **Project ref/ID**, **publishable key** (hoặc legacy `anon`) và **legacy `service_role` key** trong phần Connect/Settings → API Keys của project này.
4. Ví dụ URL `https://abcdefgh.supabase.co` thì ref là `abcdefgh`. Chỉ copy giá trị thật từ Dashboard; các ví dụ trong tài liệu không chạy được nguyên trạng.

| Giá trị | Dùng ở đâu |
|---|---|
| Project URL, ref | Cấu hình script và build APK |
| Publishable/anon key | APK; là khóa công khai |
| Service-role key | Script cấu hình chạy trên máy bạn; không đưa vào APK hoặc gửi trong chat |
| Database password | Quản lý database, không thay cho API key |

Staging là môi trường riêng với tài khoản thử mới; không cần copy dữ liệu người dùng thật. Tham chiếu [Supabase Managing Environments](https://supabase.com/docs/guides/deployment/managing-environments).

## 2. Tạo schema cho project trống

Trong **SQL Editor của `studymind-staging`**, mở lần lượt các file sau trên máy, copy toàn bộ nội dung và Run từng file. Dừng nếu có lỗi; không chạy tiếp các file phụ thuộc.

1. `C:\Projects\AI-hoc-tap\docs\sql\staging-bootstrap.sql`
2. `C:\Projects\AI-hoc-tap\supabase\migrations\001_lesson_ownership.sql`
3. `C:\Projects\AI-hoc-tap\supabase\migrations\002_studymind_pro.sql`
4. `C:\Projects\AI-hoc-tap\supabase\migrations\004_native_storage_pipeline.sql`
5. `C:\Projects\AI-hoc-tap\supabase\migrations\005_blockchain_rewards.sql`
6. `C:\Projects\AI-hoc-tap\supabase\migrations\006_reward_question_bank.sql`
7. `C:\Projects\AI-hoc-tap\supabase\migrations\007_reward_admin.sql`

**Bỏ qua `003_seed_admin.sql`** trong bước này vì file đó gắn một email cố định. Cấp admin cho tài khoản test của bạn ở bước 10.

Bootstrap chỉ dùng khi project trống chưa có `learning_sessions`. Nếu bạn đã clone đầy đủ schema từ môi trường khác, bỏ bootstrap và chỉ chạy những migration còn thiếu. Không chạy lại 005–007 nếu đã thành công.

Kiểm tra trong SQL Editor:

```sql
select count(*) as so_cau_hoi from public.studymind_reward_questions;
select enabled, chain_id from public.studymind_reward_config;
```

Kết quả ban đầu: **45 câu**, `enabled = false`, `chain_id = 80002`. Storage có các bucket `study-uploads` và `avatars`.

## 3. Chuẩn bị hai tài khoản ví và cấp gas

Có thể dùng MetaMask với **hai tài khoản thử riêng, không có tài sản thật**:

| Tài khoản | Vai trò | Có cần POL Amoy? |
|---|---|---|
| A | Admin + deployer: triển khai và quản trị contract | Có khi deploy/pause/quản trị |
| B | Operator: backend ký mint/burn cho người dùng | Có để vận hành |

Địa chỉ A và B phải khác nhau. Sau khi worker hoạt động, B chỉ dùng cho worker, tránh công cụ khác cùng gửi giao dịch và chiếm nonce.

Trong ví, thêm mạng theo [thông số Polygon chính thức](https://docs.polygon.technology/pos/reference/rpc-endpoints):

| Trường | Giá trị |
|---|---|
| Network name | Polygon Amoy |
| RPC | `https://polygon-amoy.drpc.org` |
| Chain ID | `80002` |
| Currency symbol | `POL` |
| Explorer | `https://amoy.polygonscan.com` |

Lấy **POL thử nghiệm** qua [Chainlink Polygon Amoy Faucet](https://faucets.chain.link/polygon-amoy): chọn **POL / Native**, kết nối tài khoản A, làm các bước faucet yêu cầu và chờ token về. Làm tương tự cho B, hoặc chuyển một phần POL thử từ A sang B nếu phù hợp với hạn mức faucet. Trang có cả LINK; **LINK không trả gas cho ứng dụng này**.

Nếu faucet giới hạn lượt, hết token hoặc chưa đủ gas cho deploy, chờ theo hạn mức hiển thị hoặc dùng [hướng dẫn xin POL thử từ Polygon](https://support.polygon.technology/support/solutions/articles/82000906072-how-can-i-get-test-tokens-). Không có cam kết một lượt faucet luôn đủ mọi giao dịch; phí do mạng quyết định.

POL Amoy và SC là hai thứ khác nhau: POL trả phí mạng, SC là token học tập do contract của bạn phát hành. Người học không cần POL vì operator trả gas.

Kiểm tra số dư A/B trong ví đang ở **Amoy**, hoặc tìm địa chỉ tại explorer. Faucet chỉ cần địa chỉ/kết nối ví, không cần seed phrase/private key.

## 4. Khởi tạo PowerShell và nhập cấu hình

```powershell
Set-Location -LiteralPath 'C:\Projects\AI-hoc-tap'
npm.cmd ci
npm.cmd --prefix blockchain ci

function Set-StudyMindSecret {
    param([string]$Name)
    $masked = Read-Host "Nhập $Name (nội dung sẽ được ẩn)" -AsSecureString
    $plain = [System.Net.NetworkCredential]::new('', $masked).Password
    [Environment]::SetEnvironmentVariable($Name, $plain, 'Process')
}

$env:REWARDS_STAGING_PROJECT_REF = Read-Host 'Project ref của studymind-staging'
$env:SUPABASE_URL = 'https://' + $env:REWARDS_STAGING_PROJECT_REF + '.supabase.co'
$env:REWARDS_RPC_URL = 'https://polygon-amoy.drpc.org'
$env:REWARDS_ADMIN_ADDRESS = Read-Host 'Địa chỉ 0x của tài khoản A'
$env:REWARDS_OPERATOR_ADDRESS = Read-Host 'Địa chỉ 0x của tài khoản B'

Set-StudyMindSecret 'SUPABASE_SERVICE_ROLE_KEY'
Set-StudyMindSecret 'REWARDS_DEPLOYER_KEY'
Set-StudyMindSecret 'REWARDS_OPERATOR_KEY'
```

`REWARDS_DEPLOYER_KEY` là private key **A**, `REWARDS_OPERATOR_KEY` là private key **B**; không phải seed phrase. Nếu dùng MetaMask, xem [cách xuất private key chính thức](https://support.metamask.io/configure/accounts/how-to-export-an-accounts-private-key). Nhập trực tiếp vào lời nhắc được ẩn ở PowerShell, không gửi cho tôi, không dán vào source hoặc ảnh chụp.

Những biến này chỉ tồn tại trong cửa sổ PowerShell hiện tại. Không đóng cửa sổ giữa chừng; nếu mở cửa sổ mới thì cần nhập lại. Hàm hỗ trợ chỉ được định nghĩa trên máy bạn khi bạn chạy đoạn lệnh.

## 5. Tạo bộ ví nhúng của người dùng

Bộ ví này riêng với hai tài khoản A/B. Nó dùng để tạo địa chỉ nhận SC cho từng tài khoản StudyMind.

```powershell
Set-StudyMindSecret 'STUDYMIND_VAULT_PASSWORD'
node blockchain/scripts/create-wallet-vault.mjs
```

Dùng mật khẩu mạnh ít nhất 20 ký tự và lưu lại. Script tạo file mã hóa:

```text
C:\Projects\AI-hoc-tap\.secrets\studycoin-wallet-root.keystore.json
```

Đầu ra có `xpub` và fingerprint. Lưu xpub vào biến:

```powershell
$env:REWARDS_WALLET_XPUB = Read-Host 'Dán giá trị xpub vừa tạo'
Remove-Item Env:STUDYMIND_VAULT_PASSWORD
```

Sao lưu keystore và mật khẩu ở hai nơi riêng. Server chỉ cần xpub, không cần file này hay mật khẩu. Nếu script báo file đã tồn tại, **không xóa hoặc tạo đè**; kiểm tra bộ ví đang có và dùng lại xpub tương ứng.

## 6. Deploy contract StudyCoin lên Amoy

Sau khi A có POL Amoy:

```powershell
node blockchain/scripts/deploy.mjs
```

Đầu ra thành công có dạng:

```json
{"chainId":80002,"contract":"0x...","transaction":"0x..."}
```

Lưu cả contract address và transaction hash. Mở transaction trên Amoy explorer và kiểm tra đã thành công. Sau đó:

```powershell
$env:REWARDS_TOKEN_ADDRESS = Read-Host 'Dán contract address vừa deploy'
node blockchain/scripts/configure-staging.mjs
```

Script cấu hình phải trả `configured: true`; nó kiểm tra mạng, bytecode, quyền operator và project ref trước khi ghi vào staging. Không deploy lần nữa chỉ vì quên địa chỉ; tìm lại transaction của A trên explorer. Nếu đã có ví người dùng, không thay contract/xpub bằng cách sửa database trực tiếp.

## 7. Thêm secrets cho Edge Functions staging

Trong Supabase project **staging** → **Edge Functions → Secrets**, thêm các giá trị:

| Name | Value |
|---|---|
| `REWARDS_ENABLED` | `true` |
| `REWARDS_RPC_URL` | RPC Amoy ở bước 3 |
| `REWARDS_WALLET_XPUB` | Xpub ở bước 5 |
| `REWARDS_OPERATOR_KEY` | Private key B |
| `REWARDS_CONFIRMATIONS` | `64` |
| `REWARDS_WORKER_SECRET` | Chuỗi ngẫu nhiên tạo bên dưới |
| `GEMINI_API_KEY`, `GROQ_API_KEY` | Key AI riêng cho staging nếu cần dùng phần tạo nội dung AI |

Tạo worker secret trong cùng PowerShell; lệnh gán không in giá trị ra màn hình:

```powershell
$env:REWARDS_WORKER_SECRET = node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))"
```

Có thể dùng `Set-Clipboard -Value $env:REWARDS_WORKER_SECRET` để dán vào ô Value trong Dashboard. Giữ giá trị này trên máy bạn/secret manager.

**Không thêm thủ công các tên bắt đầu bằng `SUPABASE_` vào Edge Function Secrets.** Supabase tự cấp URL và các khóa môi trường, bao gồm legacy service-role key mà code hiện dùng. Service-role key nhập ở PowerShell chỉ phục vụ script local. Không upload deployer key A, vault password hoặc keystore lên Edge Functions. Tham chiếu [Supabase Environment Variables](https://supabase.com/docs/guides/functions/secrets).

## 8. Deploy API và billing lên project staging

Trong PowerShell ở root dự án:

```powershell
if (-not (Test-Path -LiteralPath 'supabase\config.toml')) {
    npx.cmd supabase@latest init
}
npx.cmd supabase@latest login
node supabase/functions/studymind-api/build.mjs

npx.cmd supabase@latest functions deploy studymind-api --project-ref $env:REWARDS_STAGING_PROJECT_REF --no-verify-jwt --use-api
npx.cmd supabase@latest functions deploy studymind-billing --project-ref $env:REWARDS_STAGING_PROJECT_REF --no-verify-jwt --use-api
```

`--project-ref` luôn chỉ định staging; `--use-api` dùng bundler phía Supabase. Gateway JWT tắt vì các route người dùng trong code tự xác minh Supabase Auth, còn worker dùng secret riêng. Không dùng thiết lập này cho một function khác chưa có kiểm tra xác thực. Xem [Supabase CLI](https://supabase.com/docs/reference/cli/supabase-functions-deploy).

Không cần deploy `sepay-webhook` hoặc nối tài khoản ngân hàng thật để thử học → SC → Pro. API billing vẫn cần để app đọc quyền Pro.

Kiểm tra API sống:

```powershell
$apiBase = $env:SUPABASE_URL + '/functions/v1/studymind-api'
Invoke-RestMethod -Uri ($apiBase + '/api/v1/health')
```

Kết quả mong đợi có `status: online`. Các route `/api/v1/rewards/me` cần JWT người dùng, nên gọi không đăng nhập trả 401 là đúng.

## 9. Chạy worker trong lúc thử nghiệm

Chỉ deploy contract/API chưa làm job tự chạy. Worker mới là phần gửi giao dịch mint/burn và xác nhận Pro.

Trong **cùng cửa sổ PowerShell đã nhập đủ các biến ở trên**:

```powershell
$env:REWARDS_ENABLED = 'true'
$env:REWARDS_CONFIRMATIONS = '64'
node backend/rewards-worker.js
```

Nếu chưa có yêu cầu thưởng, `processed: 0` là bình thường. Sau khi kiểm tra một lượt chạy, có thể giữ worker chạy định kỳ khi thử app:

```powershell
while ($true) {
    node backend/rewards-worker.js
    Start-Sleep -Seconds 60
}
```

Giữ máy có mạng và cửa sổ này mở; nhấn **Ctrl+C** để dừng. Khi dừng, công việc chờ vẫn nằm trong database để chạy lại sau. Không chạy thêm scheduler khác trong buổi thử đầu tiên.

Để vận hành 24/7 sau buổi thử, dùng worker CLI trên máy chủ riêng hoặc scheduler riêng gọi POST `/functions/v1/studymind-api/api/internal/rewards/run` với header `x-rewards-worker-secret`. Không đưa service-role key vào request này. Chưa dùng `pg_net` để chuyển worker secret trong hướng dẫn này: [Supabase ghi nhận header của request trong queue có thể được các role có quyền database đọc](https://supabase.com/docs/guides/troubleshooting/database-roles-can-read-request-headers-queued-by-pg_net-ad6357), kể cả khi lấy secret từ Vault.

## 10. Build APK staging và tạo tài khoản thử

Ở **cửa sổ PowerShell khác**, build APK với URL/key staging. Thay đúng các giá trị mẫu:

```powershell
Set-Location -LiteralPath 'C:\Projects\AI-hoc-tap\android-native'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$stageUrl = Read-Host 'Project URL staging'
$publicKey = Read-Host 'Publishable hoặc anon key staging (không phải service-role)'
.\gradlew.bat assembleDebug "-PsupabaseUrl=$stageUrl" "-PsupabaseAnonKey=$publicKey"
```

APK ở `C:\Projects\AI-hoc-tap\android-native\app\build\outputs\apk\debug\app-debug.apk`. Cài lên thiết bị thử; đăng ký tài khoản test mới, xác minh email nếu được yêu cầu. Tài khoản production không tự xuất hiện ở project mới.

Nếu dùng Google login, cấu hình Google provider cho staging và thêm redirect `com.studymind.nativeapp://auth/callback` vào Authentication → URL Configuration. Có thể thử email/password trước để giảm bước cấu hình OAuth.

Sau khi tài khoản test xuất hiện trong Authentication → Users, dùng SQL Editor **staging** để cấp admin cho đúng email của bạn:

```sql
update public.studymind_accounts
set role = 'admin', updated_at = now()
where lower(email) = lower('EMAIL_TEST_CUA_BAN');
```

Đăng nhập lại để tải quyền mới. Nên có thêm một tài khoản thường để kiểm tra nhận xu và quyền Pro theo cách người dùng thực tế.

## 11. Thử luồng hoàn chỉnh và kiểm tra kết quả

1. Mở **Nhiệm vụ & StudyCoin**, kiểm tra có địa chỉ ví, mạng Amoy và số dư đọc được.
2. Ôn một thẻ: đọc mặt trước ≥3 giây, lật, đọc mặt sau ≥2 giây, bấm đã ôn. Nhiệm vụ bắt đầu ngày học có thể đủ điều kiện.
3. Bấm nhận 5 SC. App phải hiện chờ xử lý; worker đang chạy mới gửi mint.
4. Chờ giao dịch đủ 64 xác nhận rồi app làm mới; kiểm tra `confirmed` và số dư trên chain. Thời gian thực tế phụ thuộc block, RPC và nhịp worker, không phải tức thời.
5. Khi đủ 100 SC từ hoạt động hợp lệ, đổi Pro 1 ngày; phải thấy burn rồi mới có hạn Pro mới. Để kiểm tra nhanh ngay buổi đầu, **chỉ trên staging**, dùng tài khoản admin vào Quản trị StudyCoin đổi giá Pro 1 ngày tạm thành **5 SC**. Tài khoản thường tải lại catalog, dùng 5 SC vừa kiếm để đổi Pro và kiểm tra burn thực trên chain. Sau khi xong, admin khôi phục giá **100 SC**. Không sửa balance/status SQL hoặc tự đánh dấu giao dịch thành công để thử nhanh vì như vậy không kiểm chứng được luồng blockchain.

SQL đối soát chỉ đọc, chạy tại staging:

```sql
select kind, amount, days, status, error_code, tx_hash, created_at
from public.studymind_reward_jobs
order by created_at desc
limit 20;

select plan, pro_until
from public.studymind_accounts
where lower(email) = lower('EMAIL_TEST_CUA_BAN');
```

Không query hoặc chia sẻ `raw_transaction`, private key hay nội dung Vault khi gửi ảnh/log để hỗ trợ.

## 12. Các lỗi thường gặp

| Lỗi/trạng thái | Cách xử lý |
|---|---|
| `learning_sessions does not exist` | Project trống thiếu bootstrap ở bước 2; kiểm tra đúng project trước khi tạo |
| `Missing staging configuration` | Cửa sổ PowerShell mới/mất biến; nhập lại cấu hình local bước 4–6 |
| `insufficient funds` | Ví gửi giao dịch thiếu POL **Amoy**; nạp A khi deploy hoặc B khi chạy worker |
| Sai chain ID | RPC/ví đang ở mạng khác; dùng Amoy 80002 |
| `configured: true` nhưng app báo chưa bật | Kiểm tra Edge secrets `REWARDS_ENABLED=true`, đúng xpub và APK trỏ đúng staging |
| Job chỉ ở `queued` | Worker chưa chạy, thiếu cấu hình, thiếu gas hoặc lỗi RPC |
| Job `submitted` nhưng chưa `confirmed` | Kiểm tra explorer, đủ 64 xác nhận và worker tiếp tục chạy |
| `CONTRACT_REJECTED` | Kiểm tra quyền operator, contract pause, lượng token và điều kiện; sửa nguyên nhân rồi admin retry |
| API lỗi 401 sau đổi project | APK/Auth/key/API phải cùng staging; đăng xuất tài khoản cũ và đăng ký test mới |
| Xpub/ví đã tồn tại không khớp | Dừng; khôi phục cấu hình gốc, không tạo bộ ví khác để thay thế tùy ý |

Bạn có thể gửi **project ref, địa chỉ ví công khai, contract address, transaction hash hoặc ảnh thông báo lỗi đã che dữ liệu nhạy cảm** để được hướng dẫn tiếp. Không gửi service-role key, private key, mật khẩu hoặc seed phrase.
