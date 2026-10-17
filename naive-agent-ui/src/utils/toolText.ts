import type { ToolBlockModel } from './model'

/** 工具卡片标题文案：框架内置工具名多为英文，这里按调用状态输出中文 */
const TOOL_COPY: Record<
  string,
  {
    plain: string
    running: (skill: string) => string
    ok: (skill: string) => string
    error: (skill: string) => string
  }
> = {
  load_skill_through_path: {
    plain: '加载技能',
    running: (skill) => (skill ? `正在加载技能 ${skill}…` : '正在加载技能…'),
    ok: (skill) => (skill ? `加载 ${skill} 技能成功` : '加载技能成功'),
    error: (skill) => (skill ? `加载 ${skill} 技能失败` : '加载技能失败'),
  },
}

/** 卡片标题：已收录的工具按状态出中文，其余工具原样显示工具名 */
export function toolTitle(block: ToolBlockModel): string {
  const copy = TOOL_COPY[block.name]
  if (!copy) return block.name
  const skill = skillNameOf(block)
  if (block.status === 'running') return copy.running(skill)
  if (block.status === 'ok') return copy.ok(skill)
  if (block.status === 'error') return copy.error(skill)
  return copy.plain
}

/** 技能名：入参 skillId（如 data-chart_classpath-skills）优先，结果正文里的 Name 兜底 */
export function skillNameOf(block: ToolBlockModel): string {
  return skillNameFromArgs(block.args) || skillNameFromOutput(block.output)
}

function skillNameFromArgs(args: string): string {
  if (!args) return ''
  const skillId = parseSkillId(args)
  if (skillId) return stripNamespace(skillId)
  // 入参 JSON 还在流式拼接时，按分片取到第一个下划线为止的前缀
  return /"skillId"\s*:\s*"([^"_]+)/.exec(args)?.[1] ?? ''
}

function parseSkillId(args: string): string {
  try {
    const { skillId } = JSON.parse(args) as { skillId?: unknown }
    return typeof skillId === 'string' ? skillId : ''
  } catch {
    return ''
  }
}

/** 去掉技能仓类型后缀：sql-query_classpath-skills → sql-query；无后缀时原样返回 */
function stripNamespace(skillId: string): string {
  const i = skillId.lastIndexOf('_')
  return i > 0 ? skillId.slice(0, i) : skillId
}

function skillNameFromOutput(output: string): string {
  if (!output) return ''
  return /(?:Successfully loaded skill|Name):\s*([^\s_]+)/.exec(output)?.[1] ?? ''
}
