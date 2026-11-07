# Kế hoạch Blockchain và nhiệm vụ học tập đổi xu — Android native

**Dự án:** StudyMind (`android-native`, Java + Android View).  
**Cập nhật:** 18/10/2026.  
**Quyết định của bạn:** ĐÃ DUYỆT, chọn **phương án B — xu là token trên blockchain**.  
**Phạm vi:** Triển khai bản thử nghiệm; chưa tự triển khai production, mainnet hay phát hành cửa hàng.

## 1. Phương án B đã chọn

Người dùng học để hoàn thành nhiệm vụ; server xác minh điều kiện rồi yêu cầu smart contract **mint StudyCoin (SC)**. Khi đổi Pro, hệ thống **burn SC** và chỉ cộng hạn Pro sau khi giao dịch blockchain được xác nhận.

**Số dư có thể sử dụng do smart contract quyết định.** Database giữ bằng chứng học, nhiệm vụ, đơn đổi và bản đối soát; không có số dư xu nội bộ được chi tiêu độc lập. Phương án A ghi Merkle root đã được thay thế trong bản kế hoạch này.

| Nội dung | Thiết kế bản thử nghiệm B |
|---|---|
| Token | StudyCoin / SC, ERC-20, số nguyên, không chuyển nhượng |
| Ví | Ví nhúng do StudyMind quản lý, một địa chỉ cố định cho mỗi tài khoản |
| Khôi phục | Đăng nhập lại cùng tài khoản StudyMind; bản sao lưu ví gốc mã hóa do đơn vị vận hành giữ |
| Gas | Ví operator của hệ thống tài trợ, người học không cần mua POL |
| Quyền kiểm soát | Backend xác thực người dùng; operator có quyền mint/burn theo yêu cầu hợp lệ |
| Mạng thử nghiệm | Polygon Amoy, chain ID 80002; EVM cục bộ 31337 dùng kiểm thử |
| Xác nhận | Tối thiểu 64 block trên Amoy trước khi chốt giao dịch/cấp Pro |
| Mạng lỗi | Hiển thị chờ xử lý; chưa xác nhận thì chưa tính là token khả dụng hoặc Pro đã cấp |
| Chuyển/rút/mua bán | Chưa hỗ trợ; không có tỷ giá đổi tiền mặt |

Ví quản lý và hệ thống trả gas là lựa chọn kỹ thuật cho bản thử nghiệm để giữ trải nghiệm đăng nhập hiện có. Đây là mô hình lưu ký, không phải ví tự lưu ký. Nếu sau này cần ví ngoài, xuất khóa hay chuyển nhượng, phải thiết kế bổ sung trước khi mở các tính năng đó.

Thông số mạng tham chiếu [Polygon RPC](https://docs.polygon.technology/pos/reference/rpc-endpoints); token/quyền tham chiếu [OpenZeppelin ERC-20](https://docs.openzeppelin.com/contracts/5.x/erc20) và [Access Control](https://docs.openzeppelin.com/contracts/5.x/access-control).

## 2. Nhiệm vụ và mức thưởng đã duyệt

| Mức | Nhiệm vụ | Điều kiện | SC | Chu kỳ |
|---|---|---|---:|---|
| Dễ | Bắt đầu ngày học | Có hoạt động học hợp lệ, mở app đơn thuần không đủ | 5 | Ngày |
| Dễ | Ôn 10 thẻ thử thách | 10 thẻ khác nhau từ nguồn do server quản lý | 10 | Ngày |
| Vừa | Vượt thử thách quiz | Ít nhất 10 câu, đạt từ 80% | 20 | Ngày |
| Vừa | Tập trung 25 phút | Thời gian server, heartbeat, ít nhất 2 hoạt động học trong phiên | 10 | Ngày |
| Khó | Học liên tục 7 ngày | Mỗi ngày có hoạt động hợp lệ, không thưởng chồng trong 7 ngày | 60 | Mỗi chuỗi 7 ngày |
| Khó | Làm chủ 3 chủ đề | Đạt từ 90% ở 3 chủ đề khác nhau | 40 | Tuần |
| Cộng đồng | Mời bạn học thành công | Đủ điều kiện tại mục 3 | 200 người mời / 30 người mới | Tối đa 3 lượt/tháng/người mời |

- Tổng nhiệm vụ ngày là **45 SC**; thưởng chuỗi/tuần/giới thiệu tính riêng.
- Mốc thời gian do server tính theo `Asia/Ho_Chi_Minh`; tuần bắt đầu thứ Hai.
- Mỗi nhiệm vụ được giao có snapshot quy tắc và mức thưởng; điều chỉnh của admin áp dụng cho nhiệm vụ giao mới.
- Một nguồn thưởng chỉ tạo một `operationId`; bấm nhận nhiều lần vẫn dùng cùng thao tác on-chain.
- Không quy đổi các bộ đếm hoặc lịch sử gamification cũ thành token.
- Không thưởng bằng phần thưởng ngẫu nhiên, không bán token trong app.

Bản thử nghiệm dùng **45 câu thuộc 3 chủ đề Toán học, Khoa học, Tiếng Việt** để kiểm chứng sản phẩm. Việc học trong bài học thường cũng được tính (migration `009`): ôn **thẻ do AI tạo** (mỗi thẻ 1 lần/ngày, cách nhau ≥3 giây), **quiz bài học ≥10 câu do AI tạo** (chỉ lần nộp đầu tiên mỗi bài mỗi ngày) và **Pomodoro 25 phút** (server tính giờ qua heartbeat). Thẻ/câu hỏi người dùng tự thêm không được tính. "Làm chủ 3 chủ đề" và điều kiện quiz của giới thiệu chỉ tính ngân hàng câu hỏi riêng, vì quiz bài học hiển thị đáp án để giải thích. Phiên quiz giữ đáp án trên server, giới hạn 3 lần/ngày; phiên ôn thẻ giới hạn 2 lần/ngày. Trước phát hành rộng cần mở rộng nguồn câu hỏi, cá nhân hóa thêm và đánh giá lạm dụng; ngân hàng hiện tại không phải hệ thống thi chống gian lận chuyên dụng.

Phiên tập trung chỉ cộng thời gian được server xác nhận khi màn hình StudyCoin ở phía trước; có thể ôn thẻ hoặc làm quiz ngay trong màn hình đó. Rời màn hình/mất mạng không tự tính đủ thời gian.

## 3. Giới thiệu bạn học

1. Người mời có mã riêng và link chia sẻ.
2. Người mới nhập/xác nhận một mã trong 7 ngày đầu, trước hoạt động học nhận thưởng đầu tiên.
3. Xác minh email/tài khoản; học ít nhất **2 ngày khác nhau**, ôn **20 thẻ hợp lệ**, hoàn thành **quiz từ 80% với 10 câu**, trong 7 ngày sau gắn mã.
4. Đủ điều kiện thì chờ kiểm tra **48 giờ**. Admin có thể từ chối kèm lý do trước khi xếp hàng token.
5. Qua điều kiện, tạo hai yêu cầu mint **200 SC** và **30 SC**; từng yêu cầu chờ xác nhận blockchain. Database tạo hai công việc nguyên tử, còn hai giao dịch blockchain có thể xác nhận ở thời điểm khác nhau.
6. Người mời nhận tối đa **3 lượt được xếp thưởng trong một tháng** theo giờ Việt Nam. Vượt giới hạn phải hiển thị lý do.

Không thưởng chỉ vì bấm chia sẻ, cài app, mở link hoặc đánh giá 5 sao. Cấm tự giới thiệu, mỗi tài khoản mới chỉ có một người mời. Không đọc danh bạ. Giới hạn tài khoản/phiên và hàng chờ kiểm tra là mức bảo vệ ban đầu; phát hiện cụm tài khoản giả nâng cao cần hoàn thiện trước phát hành rộng.

Bản APK thử nghiệm có trang `referral.html`, nút mở app bằng custom scheme và nhập mã thủ công. Khi chốt Google Play/domain/chứng chỉ ký sẽ bổ sung verified HTTPS App Links và Install Referrer; không coi hai phần này đã hoàn thành trong bản APK hiện tại.

## 4. Đổi SC lấy Pro

| Phần thưởng | SC cần burn |
|---|---:|
| Pro 1 ngày | 100 |
| Pro 7 ngày | 600 |
| Pro 30 ngày | 2.400 |

Luồng xử lý:

1. App tải catalog giá/phiên bản và hiển thị xác nhận.
2. Server kiểm tra token đã xác nhận trên chain; khóa chống gửi trùng gắn với tài khoản và nội dung đơn.
3. Tạo đơn đổi và một công việc burn có snapshot giá/số ngày; mỗi ví chỉ có một burn đang chờ.
4. Worker lưu signed transaction/nonce/hash trước khi gửi lên mạng.
5. Đợi receipt hợp lệ, đúng chain/contract/ví/số SC/số ngày, block canonical và đủ số xác nhận.
6. Trong một transaction Postgres: ghi nhận job đã xác nhận, cập nhật đơn đổi, cộng hạn Pro. Gọi lại không cộng thêm ngày.

**Hạn mới = `max(now, pro_until hiện có) + số ngày đổi`.** Không tự gia hạn, không mất ngày còn lại. Cùng tài khoản thì quyền Pro có hiệu lực cả Android và web theo cơ chế dùng chung hiện có.

Burn thất bại thì không cấp Pro. Burn thành công nhưng database tạm lỗi thì worker đối soát lại và cấp Pro sau, không burn lại. Giá thay đổi trước khi xác nhận thì người dùng phải tải lại giá mới.

Đơn đổi token tách khỏi đơn SePay; số ngày đổi không được cộng vào doanh thu chuyển khoản. Gói 2.000 VND/30 ngày đang nằm trong mã billing cần được kiểm tra lại về giá thật và chi phí AI trước phát hành. Token không có tỷ giá tiền mặt.

## 5. Kiến trúc và quyền truy cập

```text
Android native → Supabase Auth → API nhiệm vụ / phiên học / đổi Pro
                                      ↓
                    Postgres: bằng chứng, hàng đợi, đơn đổi
                                      ↓
                       Worker + ví operator trả gas
                                      ↓
                    StudyCoin: mint / balanceOf / burn
                                      ↓
                 Receipt xác nhận → đối soát → cộng hạn Pro
```

- Tích hợp API vào `studymind-api` hiện có, route `/api/v1/rewards/...`; không cần tạo thêm số dư nội bộ.
- Migration `005` tạo bảng, RPC, RLS và hàng đợi; `006` tạo ngân hàng câu hỏi riêng; `007` tạo quản trị/audit.
- Dữ liệu nhạy cảm và RPC chỉ dành cho `service_role`. Người dùng không đọc đáp án trực tiếp hoặc gọi RPC tự cấp token/Pro.
- App không chứa private key, service key, mnemonic hay worker secret.
- Xpub phục vụ tạo địa chỉ; keystore ví gốc mã hóa được sao lưu ngoài server. Không đổi xpub hoặc contract của ví đã có bằng cách sửa cấu hình trực tiếp.
- Contract có admin/operator tách biệt, pause, bước chờ đổi admin và trần phát hành trọn đời 1 tỷ SC.
- Chỉ địa chỉ ví, số token và mã thao tác ngẫu nhiên xuất hiện trên chain. Không đưa email, UUID tài khoản, nội dung học hay quan hệ bạn bè lên chain. Các giao dịch ví vẫn công khai và có thể liên kết hoạt động của cùng địa chỉ.
- Dữ liệu giao dịch công khai không thể xóa khỏi blockchain. Xóa/khôi phục tài khoản cần quy trình dữ liệu và ví riêng.

## 6. Giao diện native

Mục **Nhiệm vụ & StudyCoin** có các tab Nhiệm vụ, Đổi Pro, Mời bạn và phần quản trị theo quyền:

- Số SC đã xác nhận, mint đang chờ và burn đang chờ; RPC lỗi không giả lập số dư.
- Tiến độ, độ khó, thời hạn và nút nhận từng nhiệm vụ.
- Phiên ôn thẻ, quiz riêng và phiên tập trung có thời gian server.
- Catalog đổi Pro, xác nhận burn, lịch sử token và đường dẫn explorer.
- Mã mời, nhập mã, chia sẻ và trạng thái lượt mời.
- Quản trị xem tổng mint/burn/ngày Pro, sửa mức thưởng/giá, từ chối referral có lý do và retry lỗi an toàn; mọi thay đổi có audit.

Từ Tổng quan và Tài khoản Pro có lối vào StudyCoin. Yêu cầu được buộc với JWT của đúng tài khoản để tác vụ đang chờ không sử dụng nhầm phiên đăng nhập mới. Không cache số dư để dùng khi offline.

## 7. Xử lý lỗi và giới hạn vận hành

- Trạng thái token: `queued → prepared → submitted → confirmed`, hoặc `failed`.
- Mã thao tác duy nhất trên contract là lớp chống xử lý trùng cuối cùng.
- Worker dùng lease, lưu giao dịch trước broadcast, phát lại byte cũ sau timeout và xác minh event trước khi ghi Pro.
- Giao dịch rơi/reorg trước khi chốt được giữ để đối soát/phát lại. Giao dịch kẹt phí cần vận hành; chưa có tự tăng phí.
- Độ sâu 64 xác nhận giảm rủi ro nhưng không bảo đảm tuyệt đối trước reorg sâu. Khi có reorg sâu/lộ khóa phải dừng cấp mới và đối soát, không tự sửa ledger để coi là đã hoàn tác chain.
- Thử nghiệm EVM cục bộ không thay thế kiểm thử Amoy, kiểm tra bảo mật độc lập hoặc phê duyệt cửa hàng.
- Trước mainnet cần bổ sung giám sát, ngân sách chiến dịch, chống gian lận nâng cao, đánh giá bảo mật và quy trình khóa/khôi phục.

## 8. Triển khai và trạng thái bàn giao

| Hạng mục | Trạng thái bản thử nghiệm |
|---|---|
| Contract SC, mint/burn, chống replay | Có mã nguồn và kiểm thử EVM cục bộ |
| RPC, RLS, nhiệm vụ, referral, đơn đổi | Có migration và kiểm thử PostgreSQL |
| Worker và phục hồi sau database lỗi | Có kiểm thử tích hợp SQL → EVM → Pro |
| Giao diện Android | Có mã native và APK debug build được |
| Bundle Supabase API | Có script build và bundle triển khai |
| Bản ghi deploy, input xác minh explorer | Có script, chống deploy lặp; kiểm thử tái tạo bytecode |
| Quản trị contract (pause, đổi operator, chuyển admin) | Có script chạy thử/`--yes` và kiểm thử EVM cục bộ |
| Giám sát chain/gas/hàng đợi | Có script chỉ đọc, exit code cảnh báo; chưa nối kênh thông báo |
| Ví gốc, ví operator, POL gas | Chờ cấu hình môi trường thử nghiệm; chưa tạo ví thật thay bạn |
| Contract trên Amoy | Chưa deploy |
| Migration/backend trên Supabase staging | Chưa deploy |
| Kiểm thử từ APK lên staging/Amoy | Chờ môi trường thử nghiệm |
| Mainnet / Google Play | Chưa thực hiện; cần mốc duyệt riêng |

Hướng dẫn kỹ thuật, biến môi trường và thứ tự triển khai nằm trong [hướng dẫn phương án B](C:/Projects/AI-hoc-tap/blockchain/README.md). Có script kiểm tra bytecode/quyền operator trước khi bật staging. Không tự chạy migration hoặc deploy production từ lần duyệt này.

Ước lượng cũ dành cho A không còn được dùng để cam kết thời gian của B. Phần còn lại phụ thuộc cung cấp môi trường staging, ví gas, domain/kênh phân phối và kết quả kiểm thử mạng thực.

## 9. Tiêu chí nghiệm thu

- [ ] Nhiệm vụ thực tế ghi nhận tiến độ và mint đúng phần thưởng trên Amoy.
- [ ] Token trong ví on-chain là nguồn quyết định số dư; database không có xu khả dụng song song.
- [ ] Chạm lặp/retry/hai thiết bị không mint hoặc burn trùng.
- [ ] Sửa dữ liệu học thường/SharedPreferences/giờ máy không tự tạo bằng chứng thưởng.
- [ ] Burn đúng lượng SC, đủ xác nhận rồi mới cấp Pro; DB lỗi sau burn được phục hồi đúng một lần.
- [ ] Giao dịch đang chờ/lỗi được hiển thị rõ, không giả lập thành công.
- [ ] Referral đủ điều kiện, giữ 48 giờ, đúng hạn mức tháng và không thưởng cho nút chia sẻ.
- [ ] Quyền admin, RLS, snapshot giá/quy tắc và audit đúng thiết kế.
- [ ] APK hoạt động với Supabase staging/Amoy và các tính năng học/SePay hiện có không bị ảnh hưởng.

Các test tự động cục bộ đã kiểm tra nhiều điều kiện ở trên; những ô liên quan mạng thực vẫn cần nghiệm thu staging, không tự đánh dấu đã hoàn thành.

## 10. Chính sách phát hành

Vì B cấp token cho người dùng, cần đối chiếu khai báo tính năng tài chính và nội dung token hóa theo [Google Play Blockchain-based Content](https://support.google.com/googleplay/android-developer/answer/13607354?hl=en) trước khi đưa lên Play. Luồng bán Pro bằng SePay hiện có vẫn cần được đánh giá riêng theo [Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en). Bản thử nghiệm không đồng nghĩa đã được cửa hàng chấp thuận.

**Lần duyệt của bạn đã cho phép triển khai B; không cần duyệt lại phương án A/B. Những bước chưa thực hiện ở đây là triển khai mạng thực và phát hành, cần cấu hình cụ thể và kết quả thử nghiệm trước khi tiến hành.**
