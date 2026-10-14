import React, { useEffect, useRef, useState } from 'react';
import { X, Crown, Check, Loader2, Copy, QrCode, PartyPopper, Clock, AlertTriangle } from 'lucide-react';
import { useStudy } from '../../context/StudyContext';
import { PaymentOrder } from '../../types';

export const formatVnd = (n: number) => `${n.toLocaleString('vi-VN')}đ`;

export const PRO_FEATURES = [
  'Phân tích "Chuyên sâu" (sơ đồ tư duy tới 6 tầng)',
  'Hợp nhất kiến thức đa tài liệu (Knowledge Fusion)',
  'Tạo tài liệu AI gần như không giới hạn mỗi ngày',
  'Gia sư AI trò chuyện không giới hạn'
];

const POLL_MS = 3000;

// Plan → VietQR (SePay) → webhook marks the order paid → this modal sees it by polling the order
export function UpgradeModal() {
  const { isUpgradeOpen, closeUpgrade, upgradeReason, billing, isPro, createPaymentOrder, getPaymentOrder, showToast } = useStudy();
  const [order, setOrder] = useState<PaymentOrder | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());
  const pollRef = useRef<number | null>(null);

  const stopPolling = () => {
    if (pollRef.current) window.clearInterval(pollRef.current);
    pollRef.current = null;
  };

  useEffect(() => {
    if (!isUpgradeOpen) {
      stopPolling();
      setOrder(null);
      setError(null);
    }
  }, [isUpgradeOpen]);

  // Poll the order while it waits for the bank transfer
  useEffect(() => {
    if (!order || order.status !== 'pending') return stopPolling();
    stopPolling();
    pollRef.current = window.setInterval(async () => {
      setNow(Date.now());
      const r = await getPaymentOrder(order.id).catch(() => null);
      if (r?.order && r.order.status !== order.status) {
        setOrder(prev => (prev ? { ...prev, ...r.order } : r.order!));
        if (r.order.status === 'paid') showToast('🎉 Thanh toán thành công! Chào mừng bạn đến với StudyMind Pro.');
      }
    }, POLL_MS);
    return stopPolling;
  }, [order?.id, order?.status]);

  useEffect(() => {
    if (!order || order.status !== 'pending') return;
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, [order?.id, order?.status]);

  if (!isUpgradeOpen) return null;

  const startPayment = async (planId: string) => {
    setCreating(true);
    setError(null);
    const r = await createPaymentOrder(planId);
    setCreating(false);
    if (r.order) setOrder(r.order);
    else setError(r.error || 'Không tạo được đơn thanh toán.');
  };

  // Closing never cancels the order: the user may already have sent the money from their bank app
  const close = () => closeUpgrade();

  const copy = (text: string, label: string) => {
    navigator.clipboard?.writeText(text).then(() => showToast(`Đã sao chép ${label}`)).catch(() => {});
  };

  const secondsLeft = order ? Math.max(0, Math.floor((new Date(order.expiresAt).getTime() - now) / 1000)) : 0;
  const expired = order?.status === 'expired' || (order?.status === 'pending' && secondsLeft === 0);

  return (
    <div className="fixed inset-0 z-[60] bg-slate-900/50 flex items-end sm:items-center justify-center p-0 sm:p-4" onClick={close}>
      <div
        className="bg-white w-full sm:max-w-md max-h-[92vh] overflow-y-auto rounded-t-3xl sm:rounded-3xl shadow-2xl"
        onClick={e => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Nâng cấp StudyMind Pro"
      >
        <div className="relative bg-gradient-to-br from-[#0F766E] to-[#134E4A] text-white p-6 rounded-t-3xl">
          <button onClick={close} className="absolute top-4 right-4 p-1.5 rounded-lg hover:bg-white/10" aria-label="Đóng">
            <X className="w-5 h-5" />
          </button>
          <div className="flex items-center gap-2 text-amber-300 text-xs font-bold uppercase tracking-wide">
            <Crown className="w-4 h-4" /> StudyMind Pro
          </div>
          <h2 className="text-xl font-extrabold mt-1">
            {order?.status === 'paid' ? 'Nâng cấp thành công!' : 'Học sâu hơn, không giới hạn'}
          </h2>
          {upgradeReason && !order && <p className="text-xs text-teal-100 mt-2">{upgradeReason}</p>}
        </div>

        <div className="p-6 space-y-5">
          {isPro && !order && (
            <div className="bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs rounded-xl p-3">
              Bạn đang dùng Pro{billing?.proUntil ? ` tới ${new Date(billing.proUntil).toLocaleDateString('vi-VN')}` : ''}. Thanh toán thêm sẽ cộng dồn thời hạn.
            </div>
          )}

          {!order && (
            <>
              <ul className="space-y-2">
                {PRO_FEATURES.map(f => (
                  <li key={f} className="flex items-start gap-2 text-sm text-slate-700">
                    <Check className="w-4 h-4 text-[#0F766E] shrink-0 mt-0.5" /> {f}
                  </li>
                ))}
              </ul>

              {billing && !billing.enabled && (
                <div className="flex gap-2 bg-amber-50 border border-amber-200 text-amber-800 text-xs rounded-xl p-3">
                  <AlertTriangle className="w-4 h-4 shrink-0" /> Thanh toán chưa được bật trên máy chủ. Vui lòng thử lại sau.
                </div>
              )}

              <div className="space-y-2">
                {(billing?.plans || []).map(plan => (
                  <button
                    key={plan.id}
                    disabled={creating || !billing?.enabled}
                    onClick={() => startPayment(plan.id)}
                    className="w-full flex items-center justify-between border-2 border-[#0F766E] bg-[#F0FDFA] hover:bg-[#CCFBF1] rounded-2xl px-4 py-3 text-left transition-colors disabled:opacity-60"
                  >
                    <div>
                      <p className="font-bold text-slate-900 text-sm">{plan.name}</p>
                      <p className="text-[11px] text-slate-500">{plan.days} ngày · {plan.badge || 'Thanh toán bằng chuyển khoản QR'}</p>
                    </div>
                    <span className="flex items-center gap-2 font-extrabold text-[#0F766E]">
                      {creating ? <Loader2 className="w-4 h-4 animate-spin" /> : formatVnd(plan.amount)}
                    </span>
                  </button>
                ))}
              </div>
              {error && <p className="text-xs text-rose-600">{error}</p>}
            </>
          )}

          {order?.status === 'paid' && (
            <div className="text-center space-y-3 py-4">
              <PartyPopper className="w-12 h-12 text-amber-500 mx-auto" />
              <p className="text-sm text-slate-700">
                Đã nhận {formatVnd(order.amount)}. Tài khoản của bạn đã được nâng cấp lên <b>StudyMind Pro</b>
                {billing?.proUntil ? <> tới <b>{new Date(billing.proUntil).toLocaleDateString('vi-VN')}</b></> : null}.
              </p>
              <button onClick={closeUpgrade} className="bg-[#0F766E] text-white text-sm font-bold px-6 py-2.5 rounded-xl">Bắt đầu học</button>
            </div>
          )}

          {order && order.status !== 'paid' && (
            <div className="space-y-4">
              {expired || order.status === 'cancelled' ? (
                <div className="text-center space-y-3 py-2">
                  <Clock className="w-10 h-10 text-slate-400 mx-auto" />
                  <p className="text-sm text-slate-600">Mã thanh toán đã hết hạn. Nếu bạn đã chuyển khoản, Pro vẫn sẽ được kích hoạt khi tiền về.</p>
                  <button onClick={() => setOrder(null)} className="text-sm font-bold text-[#0F766E]">Tạo mã mới</button>
                </div>
              ) : (
                <>
                  <div className="flex flex-col items-center gap-2">
                    {order.qrUrl ? (
                      <img src={order.qrUrl} alt={`Mã QR chuyển khoản ${order.code}`} className="w-56 h-56 rounded-xl border border-slate-200 bg-white" />
                    ) : (
                      <QrCode className="w-24 h-24 text-slate-300" />
                    )}
                    <p className="text-xs text-slate-500">Mở app ngân hàng → Quét QR → Xác nhận</p>
                  </div>

                  <dl className="bg-slate-50 rounded-2xl border border-slate-200 divide-y divide-slate-200 text-sm">
                    {[
                      ['Ngân hàng', order.bank?.bankCode || '', null],
                      ['Số tài khoản', order.bank?.accountNumber || '', 'số tài khoản'],
                      ...(order.bank?.accountName ? [['Chủ tài khoản', order.bank.accountName, null]] : []),
                      ['Số tiền', formatVnd(order.amount), null],
                      ['Nội dung', order.code, 'nội dung chuyển khoản']
                    ].map(([label, value, copyLabel]) => (
                      <div key={label as string} className="flex items-center justify-between px-4 py-2.5">
                        <dt className="text-slate-500 text-xs">{label}</dt>
                        <dd className="flex items-center gap-2 font-bold text-slate-900">
                          {value}
                          {copyLabel && (
                            <button onClick={() => copy(String(value), String(copyLabel))} className="p-1 rounded hover:bg-slate-200" aria-label={`Sao chép ${copyLabel}`}>
                              <Copy className="w-3.5 h-3.5 text-slate-500" />
                            </button>
                          )}
                        </dd>
                      </div>
                    ))}
                  </dl>

                  <div className="flex items-center justify-center gap-2 text-xs text-slate-500">
                    <Loader2 className="w-3.5 h-3.5 animate-spin text-[#0F766E]" />
                    Đang chờ thanh toán · còn {Math.floor(secondsLeft / 60)}:{String(secondsLeft % 60).padStart(2, '0')}
                  </div>
                  <p className="text-[11px] text-center text-slate-400">
                    Giữ nguyên nội dung <b>{order.code}</b> để hệ thống tự kích hoạt Pro trong vài giây.
                  </p>
                </>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
