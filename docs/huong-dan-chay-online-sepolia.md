# Chạy StudyCoin online trên Ethereum Sepolia (không cần bật máy tính)

Chuyển từ **chain nội bộ trên máy tính** sang **Ethereum Sepolia + Supabase Edge Function**, để app trên điện thoại dùng được ở bất cứ đâu qua Internet. Sepolia là testnet chính thức của Ethereum; **Sepolia ETH miễn phí, không có giá trị thật**, dùng để trả phí mạng.

```
Trước:  Điện thoại ─USB─► Máy tính (backend + worker + chain 31337) ─► Supabase staging
Sau:    Điện thoại ─Internet─► Edge Function studymind-api ─► Supabase staging
                                      ▲                 │
                  cron-job.org gọi mỗi phút        Ethereum Sepolia (11155111)
```

**Giá trị của bạn dùng trong tài liệu này**

| Thứ | Giá trị |
|---|---|
| Project staging | `studymind-staging`, ref `urnlxgyqmvcbkonysizy` |
| URL staging | `https://urnlxgyqmvcbkonysizy.supabase.co` |
| Ví Admin (deploy, quản trị) | StudyMind Admin `0x3Dc6A…7A7Bb` |
| Ví Operator (mint/burn) | StudyMind Operator `0x56faF…66C92` |
| RPC Sepolia | `https://ethereum-sepolia-rpc.publicnode.com` |
| Explorer | `https://sepolia.etherscan.io` |
| Thư mục dự án | `C:\Projects\AI-hoc-tap` |

**Quy tắc an toàn:** private key, mật khẩu kho ví, service_role key, worker secret **chỉ nhập vào ô ẩn của PowerShell hoặc ô của Supabase Dashboard**. Không dán vào chat, không chụp màn hình, không lưu vào file trong Git.

Ký hiệu: 🧑 = bạn tự làm · 🤖 = nhắn Claude làm giúp.

---

## Bước 1 — Xin Sepolia ETH miễn phí 🧑

| Ví | Cần | Lý do |
|---|---:|---|
| StudyMind Admin | **0,05 Sepolia ETH** | Deploy contract tốn ~1,6 triệu gas (phí Sepolia thường thấp, dư nhiều) |
| StudyMind Operator | **0,02 Sepolia ETH** | Mỗi lần mint ~165k gas, burn ~100k gas |

### Cách A — Google Cloud Web3 Faucet (nên thử trước)

Chỉ cần tài khoản Google, không đòi số dư mainnet.

1. Mở [cloud.google.com/application/web3/faucet/ethereum/sepolia](https://cloud.google.com/application/web3/faucet/ethereum/sepolia).
2. Đăng nhập Google (tài khoản của bạn).
3. **Select network:** Ethereum Sepolia.
4. **Wallet address:** dán địa chỉ **StudyMind Admin** (copy từ MetaMask).
5. Bấm **Receive** → chờ thông báo thành công.
6. Lặp lại với địa chỉ **StudyMind Operator** (nếu faucet cho phép thêm lượt trong ngày; nếu không, xem mục "Chuyển ETH" bên dưới).

### Cách B — pk910 PoW Faucet (đào bằng trình duyệt)

Không cần tài khoản; máy tính "đào" một lúc để nhận ETH.

1. Mở [sepolia-faucet.pk910.de](https://sepolia-faucet.pk910.de).
2. Dán địa chỉ **StudyMind Admin** → **Start Mining**.
3. Để tab mở 10–30 phút (máy sẽ chạy nóng hơn), theo dõi số ETH tăng.
4. Đạt ≥ 0,05 → **Stop Mining** → **Claim Rewards**.
5. Nếu trang yêu cầu Gitcoin Passport hoặc điều kiện khác mà bạn không muốn làm → dùng cách A.

### Chuyển ETH từ Admin sang Operator

1. MetaMask → chọn **StudyMind Admin** → mạng **Sepolia** (có sẵn trong danh sách test networks).
2. **Send** → chọn **StudyMind Operator** → nhập `0.02` → **Continue** → **Confirm**.

**Kiểm tra:** MetaMask → mạng **Sepolia** → số dư mỗi tài khoản, hoặc `https://sepolia.etherscan.io/address/<địa chỉ ví>`.

✅ Xong khi: Admin ≥ 0,05, Operator ≥ 0,02 Sepolia ETH.

---

## Bước 2 — Mở PowerShell và chuẩn bị hàm nhập ẩn 🧑

Dùng **một cửa sổ PowerShell duy nhất** cho bước 2 → 6 (biến mất khi đóng cửa sổ).

1. Nhấn **Windows**, gõ `PowerShell`, Enter.
2. Dán cả khối sau, Enter:

```powershell
Set-Location -LiteralPath 'C:\Projects\AI-hoc-tap'

function Set-StudyMindSecret {
    param([string]$Name)
    $masked = Read-Host "Nhập $Name (sẽ được ẩn)" -AsSecureString
    [Environment]::SetEnvironmentVariable($Name, [System.Net.NetworkCredential]::new('', $masked).Password, 'Process')
}

# Thông tin staging: service_role key lấy từ file local đã lưu, không in ra màn hình
$env:SUPABASE_URL = 'https://urnlxgyqmvcbkonysizy.supabase.co'
$env:REWARDS_STAGING_PROJECT_REF = 'urnlxgyqmvcbkonysizy'
$env:SUPABASE_SERVICE_ROLE_KEY = ((Get-Content .local\rewards-local.env | Select-String '^SUPABASE_SERVICE_ROLE_KEY=').Line -split '=', 2)[1]
$env:REWARDS_RPC_URL = 'https://ethereum-sepolia-rpc.publicnode.com'
```

✅ Không có dòng chữ đỏ nào.

---

## Bước 3 — Tạo bộ ví người học 🧑

Bộ ví này tạo địa chỉ nhận SC cho từng tài khoản StudyMind (khác ví Admin/Operator trong MetaMask).

1. Nghĩ **mật khẩu mạnh ≥ 20 ký tự**, lưu vào trình quản lý mật khẩu **trước**.
2. Trong cùng PowerShell:

```powershell
Set-StudyMindSecret 'STUDYMIND_VAULT_PASSWORD'
node blockchain/scripts/create-wallet-vault.mjs
Remove-Item Env:STUDYMIND_VAULT_PASSWORD
```

3. Kết quả:

```json
{ "xpub": "xpub6...", "fingerprint": "0x...", "vault": ".secrets/studycoin-wallet-root.keystore.json" }
```

4. Lưu xpub vào biến (dán giá trị `xpub6...`):

```powershell
$env:REWARDS_WALLET_XPUB = Read-Host 'Dán xpub'
```

5. **Sao lưu** `C:\Projects\AI-hoc-tap\.secrets\studycoin-wallet-root.keystore.json` sang USB/Drive cá nhân, và **mật khẩu ở nơi khác** file.

⚠️ Nếu báo file đã tồn tại: **không xóa**. Nhắn Claude để dùng lại xpub cũ.

✅ Xong khi: có xpub, đã sao lưu keystore + mật khẩu.

---

## Bước 4 — Deploy contract StudyCoin lên Sepolia 🧑

1. Lấy **private key StudyMind Admin**: MetaMask → **⋮** cạnh StudyMind Admin → **Account details** → **Show private key** → nhập mật khẩu MetaMask → **Copy**.
2. Trong cùng PowerShell:

```powershell
$env:REWARDS_ADMIN_ADDRESS = Read-Host 'Dán địa chỉ 0x của StudyMind Admin'
$env:REWARDS_OPERATOR_ADDRESS = Read-Host 'Dán địa chỉ 0x của StudyMind Operator'
Set-StudyMindSecret 'REWARDS_DEPLOYER_KEY'
node blockchain/scripts/deploy.mjs
Remove-Item Env:REWARDS_DEPLOYER_KEY
```

   Ở dòng `REWARDS_DEPLOYER_KEY` dán **private key Admin** (ký tự không hiện).

3. Chờ 15–60 giây. Thành công:

```json
{"chainId":11155111,"network":"Ethereum Sepolia","contract":"0xABC...","transaction":"0xDEF...","explorer":"https://sepolia.etherscan.io/address/0xABC...","record":"blockchain/deployments/sepolia-0xABC....json"}
```

4. Lưu địa chỉ contract:

```powershell
$env:REWARDS_TOKEN_ADDRESS = Read-Host 'Dán địa chỉ contract vừa deploy'
```

5. Mở link `explorer` ở trên → thấy **Contract Creation** thành công.

| Lỗi | Xử lý |
|---|---|
| `insufficient funds` / `has no Sepolia ETH` | Admin thiếu ETH → Bước 1 |
| `already deployed on Ethereum Sepolia` | Đã deploy; lấy địa chỉ trong `blockchain/deployments/sepolia-*.json` |
| `only permits Polygon Amoy … or Ethereum Sepolia` | Sai RPC → chạy lại dòng `REWARDS_RPC_URL` ở Bước 2 |
| `Use separate admin and operator wallets` | Nhập trùng địa chỉ A và B |

✅ Xong khi: có địa chỉ contract, Etherscan báo thành công.

---

## Bước 5 — Chuyển staging từ chain nội bộ sang Sepolia 🤖 + 🧑

**5a. 🤖 Nhắn Claude:** "Chạy migration 008 và xóa dữ liệu StudyCoin thử nghiệm trên staging để chuyển sang Sepolia". Claude chạy trên **studymind-staging** (không đụng production, tài khoản hay bài học):

- `supabase/migrations/008_reward_sepolia.sql` — cho phép chain `11155111`.
- Xóa dữ liệu thưởng thử nghiệm của chain nội bộ (ví `0xe5d0…`, nhiệm vụ, mã mời; 0 SC, chưa có giao dịch):

```sql
begin;
delete from public.studymind_reward_redemptions;
delete from public.studymind_referrals;
delete from public.studymind_user_missions;
delete from public.studymind_reward_jobs;
delete from public.studymind_reward_sessions;
delete from public.studymind_reward_events;
delete from public.studymind_referral_codes;
delete from public.studymind_reward_wallets;
update public.studymind_reward_config set enabled = false, token_address = null, wallet_xpub_fingerprint = null;
commit;
```

**5b. 🧑 Ghi cấu hình Sepolia** — quay lại PowerShell của bước 2–4:

1. Lấy **private key StudyMind Operator** trong MetaMask (như bước 4.1 nhưng chọn Operator).
2. Chạy:

```powershell
Set-StudyMindSecret 'REWARDS_OPERATOR_KEY'
node blockchain/scripts/configure-staging.mjs
```

3. Thành công:

```json
{"configured":true,"chainId":11155111,"contract":"0x...","project":"urnlxgyqmvcbkonysizy"}
```

Script tự kiểm tra: đúng mạng, bytecode đúng mã đã review, Operator có quyền mint/burn, đúng project staging.

✅ Xong khi: `configured: true`, `chainId: 11155111`.

---

## Bước 6 — Thêm secrets cho Edge Function 🧑

1. **Tạo worker secret** (không in ra, chép vào clipboard):

```powershell
$env:REWARDS_WORKER_SECRET = node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))"
Set-Clipboard -Value $env:REWARDS_WORKER_SECRET
```

   Dán ngay vào trình quản lý mật khẩu, tên "StudyMind worker secret" (cần lại ở Bước 8).

2. Mở `https://supabase.com/dashboard/project/urnlxgyqmvcbkonysizy/functions/secrets`.
3. Thêm từng dòng (**Name** / **Value**) rồi **Save**:

| Name | Value | Lấy ở đâu |
|---|---|---|
| `REWARDS_ENABLED` | `true` | gõ |
| `REWARDS_RPC_URL` | `https://ethereum-sepolia-rpc.publicnode.com` | gõ |
| `REWARDS_CONFIRMATIONS` | `64` | gõ (≈ 13 phút, mức finality của Sepolia) |
| `REWARDS_WALLET_XPUB` | `xpub6...` | Bước 3 |
| `REWARDS_OPERATOR_KEY` | private key Operator | MetaMask |
| `REWARDS_WORKER_SECRET` | chuỗi 64 ký tự | clipboard / trình quản lý mật khẩu |
| `GEMINI_API_KEY`, `GROQ_API_KEY` | key AI | tùy chọn, nếu dùng AI tạo bài học trên staging |

⚠️ **Không** thêm tên bắt đầu bằng `SUPABASE_`. **Không** thêm private key Admin, mật khẩu kho ví hay file keystore.

4. Xóa key khỏi PowerShell:

```powershell
Remove-Item Env:REWARDS_OPERATOR_KEY
```

✅ Xong khi: trang Secrets có đủ 6 dòng `REWARDS_*`.

---

## Bước 7 — Deploy API lên staging 🧑 + 🤖

**7a. 🧑 Đăng nhập Supabase CLI** (một lần trên máy):

```powershell
npx.cmd supabase@latest login
```

Trình duyệt mở → **Authorize** → PowerShell báo *You are now logged in*.

**7b. 🤖 Nhắn Claude:** "Deploy studymind-api lên staging". Claude build bundle, deploy `studymind-api` + `studymind-billing` lên `urnlxgyqmvcbkonysizy` và kiểm tra `/api/v1/health` trả `online`.

✅ Xong khi: health trả `online`.

---

## Bước 8 — Lịch tự gọi worker mỗi phút 🧑

1. Vào [cron-job.org](https://cron-job.org) → **Sign up** → xác minh email → đăng nhập.
2. **Cronjobs** → **Create cronjob**.
3. Tab **Common**:
   - **Title:** `StudyMind worker staging`
   - **URL:** `https://urnlxgyqmvcbkonysizy.supabase.co/functions/v1/studymind-api/api/internal/rewards/run`
   - **Execution schedule:** **Every minute**
4. Tab **Advanced**:
   - **Request method:** `POST`
   - **Headers → Add header:** Key `x-rewards-worker-secret`, Value = worker secret ở Bước 6.1
   - **Timeout:** 60 giây (hoặc tối đa cho phép)
5. **Create** → mở job → **Test run**. Đúng: HTTP **200**, nội dung có `"success":true`. HTTP 401 = sai secret.

✅ Xong khi: Test run 200, tab History có lượt chạy mỗi phút.

---

## Bước 9 — Build APK dùng Internet 🤖

Nhắn Claude: "Build APK staging trỏ vào Edge Function". Claude build **không** có `-PapiBase` (app gọi thẳng `https://urnlxgyqmvcbkonysizy.supabase.co/functions/v1/studymind-api`) và cài lên điện thoại. Từ đây **rút USB, tắt máy tính** app vẫn chạy.

---

## Bước 10 — Tắt tiến trình local 🧑

Trong khung Terminal của Claude, đóng 3 tab **chain**, **backend**, **worker** (Ctrl+C). Dữ liệu chain nội bộ vẫn nằm trong `.local/chain/` nếu sau này cần.

---

## Bước 11 — Thử từ đầu đến cuối 🧑

1. Mở app (4G/Wi-Fi, không cắm máy tính) → đăng nhập Google → **Nhiệm vụ & StudyCoin**.
2. Thấy ví mới, dòng **Ethereum Sepolia**, 0 SC.
3. Tab **Nhiệm vụ** → ôn 1 thẻ thử thách: đọc mặt trước **≥ 3 giây** → lật → đọc mặt sau **≥ 2 giây** → **Đã ôn**.
4. Nhiệm vụ **Bắt đầu ngày học** → bấm nhận 5 SC → hiện **chờ xử lý**.
5. Chờ **khoảng 15 phút** (worker mỗi phút + 64 block × 12 giây) → làm mới → **5 SC đã xác nhận**.
6. Bấm **Xem trên blockchain** → mở giao dịch trên Sepolia Etherscan.

Thử **đổi Pro** nhanh (chỉ staging): admin tạm hạ giá Pro 1 ngày còn 5 SC trong Quản trị StudyCoin (nhắn Claude cấp quyền admin cho tài khoản thử), đổi xong trả lại 100 SC.

---

## Theo dõi sau khi chạy

```powershell
$env:REWARDS_RPC_URL = 'https://ethereum-sepolia-rpc.publicnode.com'
$env:REWARDS_TOKEN_ADDRESS = '<địa chỉ contract>'
$env:REWARDS_OPERATOR_ADDRESS = '<địa chỉ Operator>'
node blockchain/scripts/status.mjs
```

| Cảnh báo | Làm gì |
|---|---|
| `OPERATOR_LOW_GAS` | Operator còn < 0,005 Sepolia ETH → xin thêm (Bước 1) hoặc chuyển từ Admin |
| `JOBS_STUCK` | Job chờ > 20 phút → kiểm tra cron-job.org History, secret, ETH của Operator, RPC |
| `JOBS_FAILED` | Có giao dịch lỗi → nhắn Claude kèm mã lỗi (không kèm key) |
| `CONTRACT_PAUSED` | Contract đang tạm dừng → [blockchain/README.md](../blockchain/README.md) mục Quản trị contract |

## Lỗi thường gặp

| Hiện tượng | Xử lý |
|---|---|
| "StudyCoin chưa được bật trên máy chủ" | Thiếu/sai `REWARDS_ENABLED=true` hoặc xpub trong Edge Function Secrets |
| "đang chờ cấu hình mạng và ví" | xpub trong Secrets khác xpub dùng ở Bước 5b |
| Nhận thưởng mãi ở "chờ xử lý" > 20 phút | cron-job.org chưa chạy / sai secret / Operator hết ETH / RPC lỗi |
| `configure-staging` báo *Existing wallets are bound…* | Chưa làm Bước 5a |
| `violates check constraint … chain_id` | Chưa chạy migration 008 (Bước 5a) |
| RPC publicnode chậm/lỗi | Đổi `REWARDS_RPC_URL` sang RPC Sepolia khác (ví dụ `https://sepolia.drpc.org`) ở cả PowerShell và Edge Function Secrets |

Khi nhờ hỗ trợ chỉ gửi địa chỉ ví, địa chỉ contract, transaction hash hoặc ảnh lỗi đã che dữ liệu nhạy cảm.
