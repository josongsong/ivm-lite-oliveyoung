/**
 * Label Showcase
 */
import { Input, Label } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function LabelShowcase() {
  return (
    <div className="showcase">
      <PageHeader
        title="Label"
        description="폼 필드의 레이블을 표시하는 컴포넌트입니다."
        stability="stable"
      />

      <LivePreview>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <Label>Default Label</Label>
          <Label required>Required Label</Label>
        </div>
      </LivePreview>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>With Form Fields</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', maxWidth: '400px' }}>
          <div>
            <Label htmlFor="username" required>Username</Label>
            <Input id="username" placeholder="Enter username" style={{ marginTop: '0.5rem' }} />
          </div>
          <div>
            <Label htmlFor="email" required>Email</Label>
            <Input id="email" type="email" placeholder="Enter email" style={{ marginTop: '0.5rem' }} />
          </div>
          <div>
            <Label htmlFor="bio">Bio (Optional)</Label>
            <Input id="bio" placeholder="Tell us about yourself" style={{ marginTop: '0.5rem' }} />
          </div>
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`<Label>Default Label</Label>
<Label required>Required Label</Label>

// With Input
<Label htmlFor="email" required>Email</Label>
<Input id="email" type="email" />`}
        </pre>
      </section>
    </div>
  )
}
