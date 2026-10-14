import React, { useEffect, useState } from 'react';
import { Crown, Check, Sparkles, Receipt, Zap, MessageCircle } from 'lucide-react';
import { useStudy } from '../../context/StudyContext';
import { PaymentOrder } from '../../types';
import { PRO_FEATURES, formatVnd } from './UpgradeModal';

const STATUS: Record<PaymentOrder['status'], { label: string; cls: string }> = {
  paid: { label: 'Đã thanh toán', cls: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  pending: { label: 'Chờ thanh toán', cls: 'bg-amber-50 text-amber-700 border-amber-200' },
  expired: { label: 'Hết hạn', cls: 'bg-slate-100 text-slate-500 border-slate-200' },
  cancelled: { label: 'Đã hủy', cls: 'bg-slate-100 text-slate-500 border-slate-200' }
};

function UsageBar({ icon: Icon, label, used, limit }: { icon: any; label: string; used: number; limit: number }) {
  const pct = Math.min(100, Math.round((used / Math.max(limit, 1)) * 100));
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-xs">
        <span className="flex items-center gap-1.5 font-semibold text-slate-700"><Icon className="w-3.5 h-3.5 text-[#0F766E]" />{label}</span>
        <span className="text-slate-500">{used}/{limit} hôm nay</span>
      </div>
      <div className="h-2 bg-slate-100 rounded-full overflow-hidden">
        <div className={`h-full rounded-full ${pct >= 90 ? 'bg-rose-500' : 'bg-[#0F766E]'}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

export function ProView() {
  const { billing, isPro, isAdmin, openUpgrade, listPaymentOrders, refreshBilling } = useStudy();
  const [orders, setOrders] = useState<PaymentOrder[]>([]);

  useEffect(() => {
    refreshBilling();
    listPaymentOrders().then(setOrders).catch(() => {});
  }, []);

  return (
    <div className="max-w-4xl mx-auto p-4 md:p-8 space-y-6">
      <div className={`rounded-3xl p-6 md:p-8 text-white shadow-lg ${isPro ? 'bg-gradient-to-br from-amber-500 to-orange-600' : 'bg-gradient-to-br from-[#0F766E] to-[#134E4A]'}`}>
        <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wide opacity-90">
          <Crown className="w-4 h-4" /> Gói của bạn
        </div>
        <h1 className="text-2xl md:text-3xl font-extrabold mt-1">
          {isAdmin ? 'Quản trị viên (Pro)' : isPro ? 'StudyMind Pro' : 'Gói Miễn phí'}
        </h1>
        <p className="text-sm opacity-90 mt-1">
          {isAdmin
            ? 'Tài khoản quản trị có toàn bộ quyền Pro.'
            : isPro && billing?.proUntil
              ? `Hiệu lực tới ${new Date(billing.proUntil).toLocaleString('vi-VN')}`
              : 'Nâng cấp để mở khóa phân tích Chuyên sâu, Knowledge Fusion và nhiều lượt AI hơn.'}
        </p>
        {!isAdmin && (
          <button onClick={() => openUpgrade()} className="mt-4 bg-white text-slate-900 font-bold text-sm px-5 py-2.5 rounded-xl shadow hover:bg-slate-50">
            {isPro ? 'Gia hạn Pro' : `Nâng cấp Pro · ${formatVnd(billing?.plans?.[0]?.amount || 2000)}`}
          </button>
        )}
      </div>

      <div className="grid md:grid-cols-2 gap-6">
        <section className="bg-white rounded-2xl border border-slate-200 p-5 space-y-4">
          <h2 className="font-bold text-slate-900 flex items-center gap-2"><Zap className="w-4 h-4 text-[#0F766E]" /> Hạn mức AI hôm nay</h2>
          {billing ? (
            <>
              <UsageBar icon={Sparkles} label="Tạo nội dung AI" used={billing.usage.generate} limit={billing.limits.generate} />
              <UsageBar icon={MessageCircle} label="Trò chuyện với gia sư AI" used={billing.usage.chat} limit={billing.limits.chat} />
              <p className="text-[11px] text-slate-400">Hạn mức được làm mới lúc 0h (giờ Việt Nam).</p>
            </>
          ) : (
            <p className="text-xs text-slate-400">Đang tải...</p>
          )}
        </section>

        <section className="bg-white rounded-2xl border border-slate-200 p-5 space-y-3">
          <h2 className="font-bold text-slate-900 flex items-center gap-2"><Crown className="w-4 h-4 text-amber-500" /> Quyền lợi Pro</h2>
          <ul className="space-y-2">
            {PRO_FEATURES.map(f => (
              <li key={f} className="flex items-start gap-2 text-sm text-slate-700">
                <Check className={`w-4 h-4 shrink-0 mt-0.5 ${isPro ? 'text-emerald-600' : 'text-slate-300'}`} /> {f}
              </li>
            ))}
          </ul>
        </section>
      </div>

      <section className="bg-white rounded-2xl border border-slate-200 p-5 space-y-3">
        <h2 className="font-bold text-slate-900 flex items-center gap-2"><Receipt className="w-4 h-4 text-[#0F766E]" /> Lịch sử thanh toán</h2>
        {orders.length === 0 ? (
          <p className="text-xs text-slate-400">Chưa có giao dịch nào.</p>
        ) : (
          <div className="divide-y divide-slate-100">
            {orders.map(o => (
              <div key={o.id} className="flex items-center justify-between py-2.5 text-sm gap-3">
                <div className="min-w-0">
                  <p className="font-semibold text-slate-800">{o.code} · {formatVnd(o.amount)}</p>
                  <p className="text-[11px] text-slate-400">{new Date(o.createdAt).toLocaleString('vi-VN')} · {o.days} ngày</p>
                </div>
                <span className={`text-[11px] font-bold px-2 py-0.5 rounded-full border shrink-0 ${STATUS[o.status].cls}`}>{STATUS[o.status].label}</span>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
