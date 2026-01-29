export const NODE_COLORS: Record<string, { bg: string; border: string; glow: string }> = {
  rawdata: {
    bg: 'rgba(0, 212, 255, 0.15)',
    border: '#00d4ff',
    glow: 'rgba(0, 212, 255, 0.4)'
  },
  slice: {
    bg: 'rgba(0, 255, 136, 0.15)',
    border: '#00ff88',
    glow: 'rgba(0, 255, 136, 0.4)'
  },
  view: {
    bg: 'rgba(255, 0, 170, 0.15)',
    border: '#ff00aa',
    glow: 'rgba(255, 0, 170, 0.4)'
  },
  sink: {
    bg: 'rgba(255, 136, 0, 0.15)',
    border: '#ff8800',
    glow: 'rgba(255, 136, 0, 0.4)'
  },
  ruleset: {
    bg: 'rgba(136, 85, 255, 0.2)',
    border: '#8855ff',
    glow: 'rgba(136, 85, 255, 0.4)'
  },
  viewdef: {
    bg: 'rgba(136, 85, 255, 0.2)',
    border: '#8855ff',
    glow: 'rgba(136, 85, 255, 0.4)'
  },
  view_def: {
    bg: 'rgba(136, 85, 255, 0.2)',
    border: '#8855ff',
    glow: 'rgba(136, 85, 255, 0.4)'
  },
  sinkrule: {
    bg: 'rgba(136, 85, 255, 0.2)',
    border: '#8855ff',
    glow: 'rgba(136, 85, 255, 0.4)'
  },
  sink_rule: {
    bg: 'rgba(136, 85, 255, 0.2)',
    border: '#8855ff',
    glow: 'rgba(136, 85, 255, 0.4)'
  }
}

export const STATUS_COLORS: Record<string, string> = {
  healthy: '#00ff88',
  warning: '#ffcc00',
  error: '#ff4444',
  inactive: '#666666'
}

export const NODE_DIMENSIONS: Record<string, { width: number; height: number }> = {
  rawdata: { width: 100, height: 65 },
  slice: { width: 100, height: 55 },
  view: { width: 130, height: 60 },
  sink: { width: 130, height: 60 },
  ruleset: { width: 80, height: 32 },
  viewdef: { width: 80, height: 32 },
  view_def: { width: 80, height: 32 },
  sinkrule: { width: 80, height: 32 },
  sink_rule: { width: 80, height: 32 }
}
