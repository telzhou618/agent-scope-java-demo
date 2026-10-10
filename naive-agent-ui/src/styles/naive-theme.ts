import type { GlobalThemeOverrides } from 'naive-ui'

/**
 * 品牌主题覆盖：靛蓝主色，明暗共用。
 * 数值与 styles/main.css 的设计令牌（--brand*）保持一致，改色请两处同步。
 * Naive 内部会基于 primaryColor 推导 hover/pressed 等派生态，这里显式给出保证一致性。
 */
export const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#6366f1',
    primaryColorHover: '#818cf8',
    primaryColorPressed: '#4f46e5',
    primaryColorSuppl: '#6366f1',
    borderRadius: '8px',
  },
  Button: {
    borderRadiusMedium: '8px',
  },
  Card: {
    borderRadius: '12px',
  },
}
