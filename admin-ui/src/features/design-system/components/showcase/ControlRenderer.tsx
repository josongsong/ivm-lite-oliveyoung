/**
 * Control Renderer - Props Playground 컨트롤 렌더링
 * Phase 3-A: Props를 조작할 수 있는 UI 컨트롤
 */

import { Input, Select, Switch } from '@/shared/ui'
import type { ControlDefinition, ControlType } from '../../data/types'
import './ControlRenderer.css'

// ============================================================================
// Types
// ============================================================================

interface ControlRendererProps {
  /** 컨트롤 이름 (prop 이름) */
  name: string
  /** 컨트롤 정의 */
  control: ControlDefinition
  /** 현재 값 */
  value: unknown
  /** 값 변경 핸들러 */
  onChange: (value: unknown) => void
}

// ============================================================================
// Individual Control Components
// ============================================================================

function TextControl({
  value,
  onChange,
  control,
}: {
  value: string
  onChange: (v: string) => void
  control: ControlDefinition
}) {
  return (
    <Input
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={control.description ?? '텍스트 입력'}
      size="sm"
    />
  )
}

function NumberControl({
  value,
  onChange,
  control,
}: {
  value: number
  onChange: (v: number) => void
  control: ControlDefinition
}) {
  return (
    <Input
      type="number"
      value={String(value)}
      onChange={(e) => onChange(Number(e.target.value))}
      min={control.min}
      max={control.max}
      step={control.step ?? 1}
      size="sm"
    />
  )
}

function BooleanControl({
  value,
  onChange,
}: {
  value: boolean
  onChange: (v: boolean) => void
}) {
  return (
    <Switch
      checked={value}
      onChange={onChange}
    />
  )
}

function SelectControl({
  value,
  onChange,
  control,
}: {
  value: string | number | boolean
  onChange: (v: string | number | boolean) => void
  control: ControlDefinition
}) {
  const options = control.options ?? []

  return (
    <Select
      value={String(value)}
      onChange={(newVal) => {
        const option = options.find((o) => String(o.value) === newVal)
        if (option) {
          onChange(option.value)
        }
      }}
      options={options.map((opt) => ({
        value: String(opt.value),
        label: opt.label,
      }))}
      size="sm"
    />
  )
}

function RadioControl({
  value,
  onChange,
  control,
  name,
}: {
  value: string | number | boolean
  onChange: (v: string | number | boolean) => void
  control: ControlDefinition
  name: string
}) {
  const options = control.options ?? []

  return (
    <div className="control-radio-group">
      {options.map((opt) => (
        <label key={String(opt.value)} className="control-radio-item">
          <input
            type="radio"
            name={`control-${name}`}
            checked={value === opt.value}
            onChange={() => onChange(opt.value)}
          />
          <span className="control-radio-label">{opt.label}</span>
        </label>
      ))}
    </div>
  )
}

function ColorControl({
  value,
  onChange,
}: {
  value: string
  onChange: (v: string) => void
}) {
  return (
    <div className="control-color">
      <input
        type="color"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="control-color-input"
      />
      <Input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        size="sm"
        style={{ flex: 1 }}
      />
    </div>
  )
}

function RangeControl({
  value,
  onChange,
  control,
}: {
  value: number
  onChange: (v: number) => void
  control: ControlDefinition
}) {
  const min = control.min ?? 0
  const max = control.max ?? 100
  const step = control.step ?? 1

  return (
    <div className="control-range">
      <input
        type="range"
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        min={min}
        max={max}
        step={step}
        className="control-range-input"
      />
      <span className="control-range-value">{value}</span>
    </div>
  )
}

function JsonControl({
  value,
  onChange,
}: {
  value: unknown
  onChange: (v: unknown) => void
}) {
  const stringValue = typeof value === 'string' ? value : JSON.stringify(value, null, 2)

  const handleChange = (newValue: string) => {
    try {
      const parsed = JSON.parse(newValue)
      onChange(parsed)
    } catch {
      // JSON 파싱 실패 시 문자열로 저장
      onChange(newValue)
    }
  }

  return (
    <textarea
      value={stringValue}
      onChange={(e) => handleChange(e.target.value)}
      className="control-json-input"
      rows={3}
    />
  )
}

// ============================================================================
// Control Renderer Mapping
// ============================================================================

const controlRenderers: Record<ControlType, React.FC<{
  value: unknown
  onChange: (v: unknown) => void
  control: ControlDefinition
  name: string
}>> = {
  text: ({ value, onChange, control }) => (
    <TextControl value={String(value ?? '')} onChange={onChange} control={control} />
  ),
  number: ({ value, onChange, control }) => (
    <NumberControl value={Number(value ?? 0)} onChange={onChange} control={control} />
  ),
  boolean: ({ value, onChange }) => (
    <BooleanControl value={Boolean(value)} onChange={onChange} />
  ),
  select: ({ value, onChange, control }) => (
    <SelectControl value={value as string | number | boolean} onChange={onChange} control={control} />
  ),
  radio: ({ value, onChange, control, name }) => (
    <RadioControl value={value as string | number | boolean} onChange={onChange} control={control} name={name} />
  ),
  color: ({ value, onChange }) => (
    <ColorControl value={String(value ?? '#000000')} onChange={onChange} />
  ),
  range: ({ value, onChange, control }) => (
    <RangeControl value={Number(value ?? 0)} onChange={onChange} control={control} />
  ),
  json: ({ value, onChange }) => (
    <JsonControl value={value} onChange={onChange} />
  ),
}

// ============================================================================
// Main Component
// ============================================================================

export function ControlRenderer({ name, control, value, onChange }: ControlRendererProps) {
  const Renderer = controlRenderers[control.type]

  if (!Renderer) {
    return (
      <div className="control-item">
        <span className="control-error">Unknown control type: {control.type}</span>
      </div>
    )
  }

  return (
    <div className="control-item">
      <div className="control-header">
        <label className="control-label">{control.label}</label>
        {control.description ? (
          <span className="control-description">{control.description}</span>
        ) : null}
      </div>
      <div className="control-input">
        <Renderer value={value} onChange={onChange} control={control} name={name} />
      </div>
    </div>
  )
}

// ============================================================================
// Controls Panel Component
// ============================================================================

interface ControlsPanelProps {
  /** 컨트롤 정의 맵 */
  controls: Record<string, ControlDefinition>
  /** 현재 값 맵 */
  values: Record<string, unknown>
  /** 값 변경 핸들러 */
  onChange: (name: string, value: unknown) => void
  /** 초기화 핸들러 */
  onReset?: () => void
}

export function ControlsPanel({ controls, values, onChange, onReset }: ControlsPanelProps) {
  const controlEntries = Object.entries(controls)

  if (controlEntries.length === 0) {
    return (
      <div className="controls-panel controls-panel--empty">
        <p>조작 가능한 Props가 없습니다.</p>
      </div>
    )
  }

  return (
    <div className="controls-panel">
      <div className="controls-panel-header">
        <h3 className="controls-panel-title">Props</h3>
        {onReset ? (
          <span
            className="controls-panel-reset"
            onClick={onReset}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => e.key === 'Enter' && onReset()}
          >
            Reset
          </span>
        ) : null}
      </div>
      <div className="controls-panel-content">
        {controlEntries.map(([name, control]) => (
          <ControlRenderer
            key={name}
            name={name}
            control={control}
            value={values[name] ?? control.defaultValue}
            onChange={(newValue) => onChange(name, newValue)}
          />
        ))}
      </div>
    </div>
  )
}
