import React from 'react';
import { useStudy } from '../../context/StudyContext';

export function Toast() {
  const { toastMessage } = useStudy();

  if (!toastMessage) return null;

  return (
    <div className="fixed bottom-20 md:bottom-6 right-6 bg-slate-900 text-white px-4 py-3 rounded-xl shadow-xl z-50 flex items-center gap-3 border border-slate-700 animate-slideUp text-xs md:text-sm font-medium">
      <span>{toastMessage}</span>
    </div>
  );
}
