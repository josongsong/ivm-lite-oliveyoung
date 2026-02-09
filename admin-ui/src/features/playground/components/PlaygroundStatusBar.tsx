import { ChevronRight } from 'lucide-react'
import type { ValidationResult } from '../types/playground'
import { type ContractType } from './playgroundConstants'

interface PlaygroundStatusBarProps {
  activeType: ContractType
  validationResult: ValidationResult | null
  lineCount: number
}

export function PlaygroundStatusBar({ activeType, validationResult, lineCount }: PlaygroundStatusBarProps) {
  return (
    <footer className="playground-statusbar">
      <div className="statusbar-left">
        <span className="status-item">
          <ChevronRight size={12} />
          {activeType}
        </span>
        {validationResult ? (
          <span className={`status-item ${validationResult.valid ? 'success' : 'error'}`}>
            {validationResult.valid ? '검증 통과' : `${validationResult.errors.length}개 오류`}
          </span>
        ) : null}
      </div>
      <div className="statusbar-right">
        <span className="status-item">Lines: {lineCount}</span>
        <span className="status-item">YAML</span>
      </div>
    </footer>
  )
}
