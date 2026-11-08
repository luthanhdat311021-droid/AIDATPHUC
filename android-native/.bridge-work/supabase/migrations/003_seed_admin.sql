-- Tài khoản quản trị StudyMind (chạy sau 002_studymind_pro.sql).
-- Quản trị viên tự động có quyền Pro và xem được trang "Quản trị" (đơn hàng, giao dịch SePay, cấp Pro thủ công).
update public.studymind_accounts set role = 'admin', updated_at = now()
where lower(email) = 'luthanhdat311021@gmail.com';
