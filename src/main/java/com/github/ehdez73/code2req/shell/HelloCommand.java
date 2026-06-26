package com.github.ehdez73.code2req.shell;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.github.ehdez73.code2req.agent.HelloResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
@ConditionalOnBean(AgentPlatform.class)
public class HelloCommand {

    private final AgentPlatform agentPlatform;

    public HelloCommand(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    @ShellMethod(key = "hello", value = "Say hello to a random person using AI")
    public String hello() {
        var invocation = AgentInvocation
            .builder(agentPlatform)
            .build(HelloResponse.class);
        return invocation.invoke("").message();
    }
}
