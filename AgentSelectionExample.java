import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.springframework.util.CollectionUtils;

// 示例代码：先过滤 agentNumbers，然后随机选一个
List<String> agentNumbers = agentUserMapper.selectAgentNumByGroupId(agentGroup.getTenantId(), agentGroup.getId());
if (CollectionUtils.isEmpty(agentNumbers)) {
    return "";
}

// 过滤：去除空值和null
List<String> filteredAgentNumbers = agentNumbers.stream()
    .filter(num -> num != null && !num.trim().isEmpty())
    .collect(Collectors.toList());

if (CollectionUtils.isEmpty(filteredAgentNumbers)) {
    return "";
}

// 随机选择一个
Random random = new Random();
String selectedAgentNumber = filteredAgentNumbers.get(random.nextInt(filteredAgentNumbers.size()));

return selectedAgentNumber;
