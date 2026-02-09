/**
 * Form Showcase - Form 컴포넌트 전시
 */
import { useState } from 'react'
import { Form, FormRow, FormGroup, FormInput, FormTextArea, Button } from '@/shared/ui'
import { PageHeader } from '../layout'
import { LivePreview } from './LivePreview'

export function FormShowcase() {
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    description: '',
  })
  const [errors, setErrors] = useState<Record<string, string>>({})

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const newErrors: Record<string, string> = {}
    if (!formData.name) newErrors.name = '이름을 입력해주세요'
    if (!formData.email) newErrors.email = '이메일을 입력해주세요'
    if (!formData.description) newErrors.description = '설명을 입력해주세요'
    
    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors)
    } else {
      setErrors({})
      alert('폼 제출 성공!')
    }
  }

  return (
    <div className="showcase">
      <PageHeader
        title="Form"
        description="폼 컨테이너 및 폼 관련 컴포넌트들입니다."
        stability="stable"
      />

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>기본 폼</h3>
        <LivePreview>
          <Form onSubmit={handleSubmit}>
            <FormRow>
              <FormGroup label="이름" htmlFor="name" error={errors.name}>
                <FormInput
                  id="name"
                  name="name"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="이름을 입력하세요"
                />
              </FormGroup>
              <FormGroup label="이메일" htmlFor="email" flex={2} error={errors.email}>
                <FormInput
                  id="email"
                  type="email"
                  name="email"
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  placeholder="email@example.com"
                />
              </FormGroup>
            </FormRow>
            <FormGroup label="설명" htmlFor="description" error={errors.description}>
              <FormTextArea
                id="description"
                name="description"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                placeholder="설명을 입력하세요"
                rows={4}
              />
            </FormGroup>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '1rem' }}>
              <Button type="button" variant="ghost">취소</Button>
              <Button type="submit" variant="primary">제출</Button>
            </div>
          </Form>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>에러 상태</h3>
        <LivePreview>
          <FormGroup label="이름" htmlFor="name-error" error="이름을 입력해주세요">
            <FormInput
              id="name-error"
              error
              placeholder="이름을 입력하세요"
            />
          </FormGroup>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Helper Text</h3>
        <LivePreview>
          <FormGroup label="이메일" htmlFor="email-helper" helperText="이메일 주소를 입력해주세요">
            <FormInput
              id="email-helper"
              type="email"
              placeholder="email@example.com"
            />
          </FormGroup>
        </LivePreview>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h3 style={{ marginBottom: '1rem' }}>Code</h3>
        <pre style={{ background: 'var(--bg-tertiary)', padding: '1rem', borderRadius: '8px', overflow: 'auto' }}>
{`import { Form, FormRow, FormGroup, FormInput, FormTextArea } from '@/shared/ui'

<Form onSubmit={handleSubmit}>
  <FormRow>
    <FormGroup label="이름" htmlFor="name" error={errors.name}>
      <FormInput id="name" name="name" />
    </FormGroup>
    <FormGroup label="이메일" htmlFor="email" flex={2} error={errors.email}>
      <FormInput id="email" type="email" />
    </FormGroup>
  </FormRow>
  <FormGroup label="설명" htmlFor="description">
    <FormTextArea id="description" rows={4} />
  </FormGroup>
</Form>`}
        </pre>
      </section>
    </div>
  )
}
