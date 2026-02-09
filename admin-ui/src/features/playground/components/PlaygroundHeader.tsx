import { Code2, Command, Play, RotateCcw, Share2 } from 'lucide-react'
import { Button, IconButton, Tabs, TabsList, TabsTrigger } from '@/shared/ui'
import { CONTRACT_TYPES, type ContractType } from './playgroundConstants'

interface PlaygroundHeaderProps {
  activeType: ContractType
  onTypeChange: (type: ContractType) => void
  onRun: () => void
  onShare: () => void
  onReset: () => void
  isSimulating: boolean
}

export function PlaygroundHeader({
  activeType,
  onTypeChange,
  onRun,
  onShare,
  onReset,
  isSimulating,
}: PlaygroundHeaderProps) {
  return (
    <header className="playground-header">
      <div className="header-left">
        <div className="header-title">
          <Code2 size={18} />
          <span>Contract Playground</span>
        </div>
        <span className="header-hint">
          <Command size={12} />+Enter 실행
        </span>
      </div>

      <Tabs value={activeType} onValueChange={(v) => onTypeChange(v as ContractType)}>
        <TabsList className="header-tabs compact">
          {CONTRACT_TYPES.map(({ type, label, icon: Icon }) => (
            <TabsTrigger key={type} value={type} icon={<Icon size={14} />}>
              {label}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <div className="header-actions">
        <Button
          variant="primary"
          size="sm"
          onClick={onRun}
          disabled={isSimulating}
          loading={isSimulating}
          icon={<Play size={14} />}
        >
          {isSimulating ? '실행중...' : '실행'}
        </Button>

        <IconButton icon={Share2} size="sm" variant="ghost" onClick={onShare} tooltip="공유 링크 복사" />
        <IconButton icon={RotateCcw} size="sm" variant="ghost" onClick={onReset} tooltip="초기화" />
      </div>
    </header>
  )
}
