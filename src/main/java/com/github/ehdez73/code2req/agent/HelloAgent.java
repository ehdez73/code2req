package com.github.ehdez73.code2req.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;

@Agent(description = "hello")
public class HelloAgent {

    @Action
    public SuggestedName suggestName(String input, OperationContext context) {
        return context.ai()
            .withDefaultLlm()
            .createObject("Suggest a random person's first name. Return only the name.", SuggestedName.class);
    }

    @Action
    public SuggestedAge suggestAge(String input, OperationContext context) {
        return context.ai()
            .withDefaultLlm()
            .createObject("Pick a random age between 18 and 80. Return only the number.", SuggestedAge.class);
    }

    @Action
    public SuggestedSurname suggestSurname(SuggestedName name, OperationContext context) {
        return context.ai()
            .withDefaultLlm()
            .createObject("""
                Suggest a random surname (last name) that would pair well with the first name "%s".
                Return only the surname.""".formatted(name.value()), SuggestedSurname.class);
    }

    @AchievesGoal(description = "Greet a randomly suggested person")
    @Action
    public HelloResponse sayHello(SuggestedName name, SuggestedSurname surname, SuggestedAge age) {
        return new HelloResponse("Hello " + name.value() + " " + surname.value() + "! You are " + age.value() + " years old.");
    }
}
