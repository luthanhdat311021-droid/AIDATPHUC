import React, { useState, useEffect } from 'react';
import { X, Camera, Upload, Link, Check, User as UserIcon, Loader2 } from 'lucide-react';
import { useStudy } from '../../context/StudyContext';

const PRESET_AVATARS = [
  "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80",
  "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150&auto=format&fit=crop&q=80",
  "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150&auto=format&fit=crop&q=80",
  "https://images.unsplash.com/photo-1580489944761-15a19d654956?w=150&auto=format&fit=crop&q=80",
  "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80"
];

export function EditProfileModal() {
  const { user, isEditProfileOpen, closeEditProfileModal, updateUserProfile, uploadAvatarFile } = useStudy();

  const [fullName, setFullName] = useState<string>(user?.fullName || '');
  const [avatarUrl, setAvatarUrl] = useState<string>(user?.avatarUrl || PRESET_AVATARS[0]);
  const [isUrlInputMode, setIsUrlInputMode] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(false);
  const [uploadingAvatar, setUploadingAvatar] = useState<boolean>(false);

  useEffect(() => {
    if (isEditProfileOpen && user) {
      setFullName(user.fullName || '');
      setAvatarUrl(user.avatarUrl || PRESET_AVATARS[0]);
    }
  }, [isEditProfileOpen, user]);

  if (!isEditProfileOpen) return null;

  const handleAvatarFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      setUploadingAvatar(true);
      try {
        const res = await uploadAvatarFile(file);
        if (res.success && res.avatarUrl) {
          setAvatarUrl(res.avatarUrl);
        }
      } finally {
        setUploadingAvatar(false);
      }
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      await updateUserProfile(fullName, avatarUrl);
      closeEditProfileModal();
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in">
      <div className="bg-white max-w-lg w-full rounded-3xl shadow-2xl border border-slate-200 overflow-hidden relative animate-in zoom-in-95">
        
        {/* Header */}
        <div className="p-6 border-b border-slate-100 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-teal-50 text-[#0F766E] flex items-center justify-center font-bold">
              <UserIcon className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-[#111827]">Chỉnh sửa hồ sơ cá nhân</h3>
              <p className="text-xs text-slate-500">Tùy chỉnh Họ tên và Ảnh đại diện hiển thị của bạn</p>
            </div>
          </div>
          <button
            onClick={closeEditProfileModal}
            className="p-2 text-slate-400 hover:text-slate-600 rounded-full hover:bg-slate-100 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-6">
          
          {/* Avatar Section */}
          <div className="flex flex-col items-center gap-4">
            <div className="relative group">
              <img
                src={avatarUrl}
                alt={fullName}
                className="w-24 h-24 rounded-full object-cover ring-4 ring-[#CCFBF1] shadow-md"
                onError={(e) => {
                  (e.target as HTMLImageElement).src = PRESET_AVATARS[0];
                }}
              />
              {uploadingAvatar && (
                <div className="absolute inset-0 bg-slate-900/50 rounded-full flex items-center justify-center text-white">
                  <Loader2 className="w-6 h-6 animate-spin" />
                </div>
              )}
            </div>

            {/* Avatar Input Options */}
            <div className="flex items-center gap-2">
              <label className="inline-flex items-center gap-2 px-3.5 py-2 bg-slate-100 hover:bg-slate-200/80 text-slate-700 text-xs font-bold rounded-xl cursor-pointer transition-colors shadow-2xs">
                <Upload className="w-4 h-4 text-[#0F766E]" />
                <span>Tải ảnh từ máy</span>
                <input
                  type="file"
                  accept="image/*"
                  onChange={handleAvatarFileChange}
                  className="hidden"
                />
              </label>

              <button
                type="button"
                onClick={() => setIsUrlInputMode(!isUrlInputMode)}
                className="inline-flex items-center gap-2 px-3.5 py-2 bg-slate-100 hover:bg-slate-200/80 text-slate-700 text-xs font-bold rounded-xl transition-colors shadow-2xs"
              >
                <Link className="w-4 h-4 text-[#0F766E]" />
                <span>{isUrlInputMode ? "Ẩn URL" : "Nhập URL ảnh"}</span>
              </button>
            </div>

            {/* URL Input field if toggled */}
            {isUrlInputMode && (
              <div className="w-full animate-in fade-in">
                <input
                  type="url"
                  value={avatarUrl}
                  onChange={(e) => setAvatarUrl(e.target.value)}
                  placeholder="Dán đường dẫn URL ảnh đại diện của bạn..."
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-2.5 text-xs text-[#111827] focus:outline-none focus:border-[#0F766E] focus:ring-1 focus:ring-[#0F766E]"
                />
              </div>
            )}

            {/* Preset Avatars */}
            <div className="w-full space-y-2 pt-2">
              <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider text-center">
                Hoặc chọn mẫu ảnh có sẵn:
              </label>
              <div className="flex items-center justify-center gap-3">
                {PRESET_AVATARS.map((preset, idx) => {
                  const isSelected = avatarUrl === preset;
                  return (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => setAvatarUrl(preset)}
                      className={`relative rounded-full overflow-hidden transition-all duration-200 ${
                        isSelected ? 'ring-3 ring-[#0F766E] scale-110 shadow-md' : 'opacity-70 hover:opacity-100 hover:scale-105'
                      }`}
                    >
                      <img src={preset} alt={`Preset ${idx}`} className="w-10 h-10 object-cover" />
                      {isSelected && (
                        <div className="absolute inset-0 bg-[#0F766E]/30 flex items-center justify-center text-white">
                          <Check className="w-4 h-4 stroke-[3]" />
                        </div>
                      )}
                    </button>
                  );
                })}
              </div>
            </div>
          </div>

          {/* User Fields */}
          <div className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Họ và Tên
              </label>
              <input
                type="text"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="Nhập họ và tên của bạn..."
                className="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-xs md:text-sm font-semibold text-[#111827] focus:bg-white focus:outline-none focus:border-[#0F766E] focus:ring-2 focus:ring-[#0F766E]/20 transition-all"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1.5">
                Địa chỉ Email
              </label>
              <input
                type="email"
                disabled
                value={user?.email || 'user@studymind.ai'}
                className="w-full bg-slate-100 border border-slate-200 rounded-xl px-4 py-3 text-xs font-medium text-slate-500 cursor-not-allowed"
              />
            </div>
          </div>

          {/* Action Footer Buttons */}
          <div className="flex items-center gap-3 pt-3 border-t border-slate-100">
            <button
              type="button"
              onClick={closeEditProfileModal}
              className="flex-1 py-3 px-4 bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold rounded-xl transition-colors"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 py-3 px-4 bg-[#0F766E] hover:bg-[#0D645E] text-white text-xs font-bold rounded-xl shadow-md transition-all flex items-center justify-center gap-2"
            >
              {loading ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <span>Lưu thay đổi</span>
              )}
            </button>
          </div>

        </form>

      </div>
    </div>
  );
}
