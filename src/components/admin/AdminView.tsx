import React, { useEffect, useState } from 'react';
import { ShieldCheck, RefreshCw, Loader2, Wallet, Users, Receipt, Landmark } from 'lucide-react';
import { useStudy } from '../../context/StudyContext';
import { AdminOverview } from '../../types';
import { formatVnd } from '../pro/UpgradeModal';

const RESULT_LABEL: Record<string, string> = {
  paid: 'Kích hoạt Pro',
  already_paid: 'Trùng (đã xử lý)',
  underpaid: 'Thiếu tiền',
  no_order: 'Không khớp đơn',
  ignored: 'Bỏ qua'
};

// Only reachable for role = admin; the server re-checks the role on every /api/admin call
export function AdminView() {
  const { isAdmin, fetchAdminOverview, adminSetPro, showToast } = useStudy();
  const [data, setData] = useState<AdminOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [email, setEmail] = useState('');
  const [days, setDays] = useState(30);
  const [saving, setSaving] = useState(false);

  const load = async () => {
    setLoading(true);
    const r = await fetchAdminOverview();
    setLoading(false);
    if (r.data) { setData(r.data); setError(null); } else setError(r.error || 'Không tải được dữ liệu.');
  };

  useEffect(() => { if (isAdmin) load(); }, [isAdmin]);

  if (!isAdmin) {
    return <div className="max-w-xl mx-auto my-16 text-center text-sm text-slate-500">Bạn không có quyền truy cập trang này.</div>;
  }

  const submit = async (grantDays: number) => {
    if (!email.trim()) return;
    setSaving(true);
    const r = await adminSetPro(email.trim(), grantDays);
    setSaving(false);
    if (r.success) {
      showToast(grantDays > 0 ? `Đã cộng ${grantDays} ngày Pro cho ${email}` : `Đã thu hồi Pro của ${email}`);
      load();
    } else showToast(r.error || 'Không cập nhật được.');
  };

  return (
    <div className="max-w-6xl mx-auto p-4 md:p-8 space-y-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-xl md:text-2xl font-extrabold text-slate-900 flex items-center gap-2">
          <ShieldCheck className="w-6 h-6 text-[#0F766E]" /> Quản trị StudyMind
        </h1>
        <button onClick={load} disabled={loading} className="flex items-center gap-1.5 text-xs font-bold text-[#0F766E] border border-[#0F766E] px-3 py-1.5 rounded-lg disabled:opacity-50">
          {loading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCw className="w-3.5 h-3.5" />} Làm mới
        </button>
      </div>

      {error && <div className="bg-rose-50 border border-rose-200 text-rose-700 text-sm rounded-xl p-3">{error}</div>}

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          { icon: Wallet, label: 'Doanh thu', value: data ? formatVnd(data.revenue) : '—' },
          { icon: Receipt, label: 'Đơn đã thanh toán', value: data?.paidCount ?? '—' },
          { icon: Users, label: 'Thành viên Pro', value: data ? data.members.filter(m => !m.isAdmin).length : '—' },
          { icon: Landmark, label: 'Giao dịch SePay', value: data?.transactions.length ?? '—' }
        ].map(({ icon: Icon, label, value }) => (
          <div key={label} className="bg-white border border-slate-200 rounded-2xl p-4">
            <Icon className="w-4 h-4 text-[#0F766E]" />
            <p className="text-lg font-extrabold text-slate-900 mt-1">{value}</p>
            <p className="text-[11px] text-slate-500">{label}</p>
          </div>
        ))}
      </div>

      <section className="bg-white border border-slate-200 rounded-2xl p-5 space-y-3">
        <h2 className="font-bold text-slate-900 text-sm">Cấp / thu hồi Pro thủ công</h2>
        <div className="flex flex-col sm:flex-row gap-2">
          <input value={email} onChange={e => setEmail(e.target.value)} type="email" placeholder="email@nguoidung.com"
            className="flex-1 border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-[#0F766E]" />
          <select value={days} onChange={e => setDays(Number(e.target.value))} className="border border-slate-200 rounded-lg px-3 py-2 text-sm">
            {[7, 30, 90, 365].map(d => <option key={d} value={d}>{d} ngày</option>)}
          </select>
          <button onClick={() => submit(days)} disabled={saving} className="bg-[#0F766E] text-white text-sm font-bold px-4 py-2 rounded-lg disabled:opacity-50">Cấp Pro</button>
          <button onClick={() => submit(0)} disabled={saving} className="border border-rose-300 text-rose-600 text-sm font-bold px-4 py-2 rounded-lg disabled:opacity-50">Thu hồi</button>
        </div>
      </section>

      <div className="grid lg:grid-cols-2 gap-6">
        <section className="bg-white border border-slate-200 rounded-2xl p-5">
          <h2 className="font-bold text-slate-900 text-sm mb-2">Đơn thanh toán gần đây</h2>
          <div className="divide-y divide-slate-100 max-h-96 overflow-y-auto">
            {(data?.orders || []).map(o => (
              <div key={o.id} className="py-2 text-xs flex justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-semibold text-slate-800 truncate">{o.code} · {o.email || 'không rõ'}</p>
                  <p className="text-slate-400">{new Date(o.createdAt).toLocaleString('vi-VN')}</p>
                </div>
                <div className="text-right shrink-0">
                  <p className="font-bold">{formatVnd(o.amount)}</p>
                  <p className={o.status === 'paid' ? 'text-emerald-600' : 'text-slate-400'}>{o.status}</p>
                </div>
              </div>
            ))}
            {data && data.orders.length === 0 && <p className="text-xs text-slate-400 py-2">Chưa có đơn.</p>}
          </div>
        </section>

        <section className="bg-white border border-slate-200 rounded-2xl p-5">
          <h2 className="font-bold text-slate-900 text-sm mb-2">Webhook SePay</h2>
          <div className="divide-y divide-slate-100 max-h-96 overflow-y-auto">
            {(data?.transactions || []).map(t => (
              <div key={t.sepay_id} className="py-2 text-xs flex justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-semibold text-slate-800 truncate">#{t.sepay_id} · {t.gateway || ''}</p>
                  <p className="text-slate-400 truncate">{t.content}</p>
                </div>
                <div className="text-right shrink-0">
                  <p className="font-bold">{formatVnd(t.amount)}</p>
                  <p className={t.result === 'paid' ? 'text-emerald-600' : t.result === 'underpaid' || t.result === 'no_order' ? 'text-amber-600' : 'text-slate-400'}>
                    {RESULT_LABEL[t.result] || t.result}
                  </p>
                </div>
              </div>
            ))}
            {data && data.transactions.length === 0 && <p className="text-xs text-slate-400 py-2">Chưa nhận giao dịch nào.</p>}
          </div>
        </section>
      </div>

      <section className="bg-white border border-slate-200 rounded-2xl p-5">
        <h2 className="font-bold text-slate-900 text-sm mb-2">Thành viên Pro & quản trị</h2>
        <div className="divide-y divide-slate-100">
          {(data?.members || []).map(m => (
            <div key={m.id} className="py-2 text-xs flex justify-between gap-2">
              <span className="font-semibold text-slate-800 truncate">{m.email}</span>
              <span className="text-slate-500 shrink-0">
                {m.isAdmin ? 'Quản trị viên' : m.proUntil ? `Pro tới ${new Date(m.proUntil).toLocaleDateString('vi-VN')}` : ''}
              </span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
