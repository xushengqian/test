package com.ivr.system.handler.impl;

import com.ivr.system.handler.NodeHandler;
import com.ivr.system.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 条件判断节点处理器
 */
@Slf4j
@Component
public class ConditionNodeHandler implements NodeHandler {

    @Override
    public IvrNode.NodeType getNodeType() {
        return IvrNode.NodeType.CONDITION;
    }

    @Override
    public IvrResult execute(CallSession session, IvrNode node, IvrFlow flow) {
        log.info("Executing condition node [{}] for session [{}]", node.getId(), session.getSessionId());

        try {
            Map<String, Object> params = node.getParams();
            if (params == null) {
                return IvrResult.success(node.getDefaultNextNode());
            }

            String variableName = (String) params.get("variable");
            String operator = (String) params.get("operator");
            Object expectedValue = params.get("value");
            String trueNode = (String) params.get("trueNode");
            String falseNode = (String) params.get("falseNode");

            if (variableName == null || operator == null) {
                log.warn("Condition node missing variable or operator");
                return IvrResult.success(node.getDefaultNextNode());
            }

            Object actualValue = session.getVariable(variableName);
            boolean conditionMet = evaluateCondition(actualValue, operator, expectedValue);

            log.debug("Condition: {} {} {} = {}", variableName, operator, expectedValue, conditionMet);

            String nextNode = conditionMet ?
                    (trueNode != null ? trueNode : node.getDefaultNextNode()) :
                    (falseNode != null ? falseNode : node.getDefaultNextNode());

            return IvrResult.success(nextNode);
        } catch (Exception e) {
            log.error("Error executing condition node: {}", e.getMessage(), e);
            return IvrResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }

    /**
     * 评估条件
     */
    private boolean evaluateCondition(Object actual, String operator, Object expected) {
        if (actual == null) {
            return "null".equals(operator) || "isEmpty".equals(operator);
        }

        return switch (operator) {
            case "equals", "==" -> actual.equals(expected);
            case "notEquals", "!=" -> !actual.equals(expected);
            case "contains" -> actual.toString().contains(expected.toString());
            case "startsWith" -> actual.toString().startsWith(expected.toString());
            case "endsWith" -> actual.toString().endsWith(expected.toString());
            case "gt", ">" -> compareNumbers(actual, expected) > 0;
            case "gte", ">=" -> compareNumbers(actual, expected) >= 0;
            case "lt", "<" -> compareNumbers(actual, expected) < 0;
            case "lte", "<=" -> compareNumbers(actual, expected) <= 0;
            case "isEmpty" -> actual.toString().isEmpty();
            case "isNotEmpty" -> !actual.toString().isEmpty();
            case "null" -> false;
            case "notNull" -> true;
            default -> {
                log.warn("Unknown operator: {}", operator);
                yield false;
            }
        };
    }

    /**
     * 比较数字
     */
    private int compareNumbers(Object a, Object b) {
        try {
            double numA = Double.parseDouble(a.toString());
            double numB = Double.parseDouble(b.toString());
            return Double.compare(numA, numB);
        } catch (NumberFormatException e) {
            return a.toString().compareTo(b.toString());
        }
    }
}
