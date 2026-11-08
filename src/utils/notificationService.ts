import { LocalNotifications } from '@capacitor/local-notifications';
import { Capacitor } from '@capacitor/core';

export interface NotificationScheduleOptions {
  title: string;
  body: string;
  delaySeconds: number;
  id?: number;
}

class NotificationService {
  private hasPermission: boolean = false;

  async init() {
    try {
      if (Capacitor.isNativePlatform()) {
        const check = await LocalNotifications.checkPermissions();
        if (check.display !== 'granted') {
          const req = await LocalNotifications.requestPermissions();
          this.hasPermission = req.display === 'granted';
        } else {
          this.hasPermission = true;
        }
      } else if (typeof window !== 'undefined' && 'Notification' in window) {
        if (Notification.permission === 'granted') {
          this.hasPermission = true;
        } else if (Notification.permission !== 'denied') {
          const perm = await Notification.requestPermission();
          this.hasPermission = perm === 'granted';
        }
      }
    } catch (err) {
      console.warn('Notification init error:', err);
    }
  }

  async scheduleNotification({ title, body, delaySeconds, id }: NotificationScheduleOptions): Promise<boolean> {
    const notifId = id || Math.floor(Math.random() * 1000000) + 1;
    try {
      if (Capacitor.isNativePlatform()) {
        await this.init();
        await LocalNotifications.schedule({
          notifications: [
            {
              id: notifId,
              title,
              body,
              schedule: { at: new Date(Date.now() + delaySeconds * 1000) },
              sound: 'beep.wav',
              smallIcon: 'ic_launcher_round'
            }
          ]
        });
        return true;
      } else if (typeof window !== 'undefined' && 'Notification' in window && Notification.permission === 'granted') {
        setTimeout(() => {
          new Notification(title, { body, icon: '/favicon.svg' });
        }, delaySeconds * 1000);
        return true;
      }
    } catch (err) {
      console.warn('Failed to schedule notification:', err);
    }
    return false;
  }

  // Schedule Spaced Repetition Flashcard Review
  async scheduleFlashcardReview(subject: string, nextReview?: string) {
    const delaySeconds = Math.round((Date.parse(nextReview || '') - Date.now()) / 1000);
    if (!Number.isFinite(delaySeconds) || delaySeconds <= 0) return false;
    return this.scheduleNotification({
      title: 'StudyMind AI - Nhắc nhở Ôn tập Flashcard',
      body: `⏰ Đến lúc ôn lại thẻ ghi nhớ của môn [${subject}]!`,
      delaySeconds
    });
  }

  // Schedule Daily Study Reminder (e.g. 20:00 every day)
  async scheduleDailyReminder(hour: number = 20, minute: number = 0) {
    const now = new Date();
    const scheduled = new Date();
    scheduled.setHours(hour, minute, 0, 0);
    if (scheduled <= now) {
      scheduled.setDate(scheduled.getDate() + 1);
    }
    const delaySeconds = Math.round((scheduled.getTime() - now.getTime()) / 1000);

    return this.scheduleNotification({
      id: 9999,
      title: 'StudyMind AI - Giờ vàng học tập 🔥',
      body: 'Dành 15 phút hôm nay để giữ vững chuỗi ngày học tập (Streak) của bạn!',
      delaySeconds
    });
  }
}

export const notificationService = new NotificationService();
