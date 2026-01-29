import { RawDataNode } from './RawDataNode'
import { SliceNode } from './SliceNode'
import { ViewNode } from './ViewNode'
import { SinkNode } from './SinkNode'
import { RuleNode } from './RuleNode'
import { ViewDefNode } from './ViewDefNode'
import { SinkRuleNode } from './SinkRuleNode'

export const nodeTypes = {
  rawdata: RawDataNode,
  slice: SliceNode,
  view: ViewNode,
  sink: SinkNode,
  ruleset: RuleNode,
  viewdef: ViewDefNode,
  view_def: ViewDefNode,   // BE에서 언더스코어로 보냄
  sinkrule: SinkRuleNode,
  sink_rule: SinkRuleNode, // BE에서 언더스코어로 보냄
}

export { RawDataNode, SliceNode, ViewNode, SinkNode, RuleNode, ViewDefNode, SinkRuleNode }
