package customlistener.monitoring.data;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.io.File;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;

import static customlistener.JUnit5Listener.BYTE_BUDDY_ARGS;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class ByteBuddyManager {

    public ByteBuddyManager() {
    }

    public static void attachByteBuddy() {
           // Install ASM Transformer
        Instrumentation instrumentation = ByteBuddyAgent.install();
        //instrumentation.addTransformer(new ASMTransformer(), true);
        //instrumentation.addTransformer(new SootTransformer(), true);

        // Install ByteBuddy Agent for Method Profiling
//        PrintStream bytebBuddyLogs = null;
//        try {
//            bytebBuddyLogs = new PrintStream("/home/listener/output/logs/csv/pisimibrt.txt");
//        }catch (Exception e){
//            e.printStackTrace();
//        }
        new AgentBuilder.Default()
//                .ignore(ElementMatchers.nameStartsWith("java.")
//                        .or(ElementMatchers.nameStartsWith("javax."))
//                        .or(ElementMatchers.nameStartsWith("sun."))
//                        .or(ElementMatchers.nameStartsWith("customlistener.")))
//                .with(new AgentBuilder.Listener.StreamWriting(writeToEngineer))  // Logs to stdout
//                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .type(ElementMatchers.any())
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.method(methodsInByteBuddyArgs())
                                .intercept(Advice.to(MethodProfiler.class))
                )
                .installOn(instrumentation);

    }

    private static ElementMatcher<? super MethodDescription> methodsInByteBuddyArgs() {
        // Start by creating a matcher for the first method name in BYTE_BUDDY_ARGS

        if(BYTE_BUDDY_ARGS[0].equals("all")){
            return any();
        }
        ElementMatcher.Junction<MethodDescription> methodMatcher = named(BYTE_BUDDY_ARGS[0]);

        for (int i = 1; i < BYTE_BUDDY_ARGS.length; i++) {
            methodMatcher = methodMatcher.or(named(BYTE_BUDDY_ARGS[i]));
        }
        return methodMatcher;
    }


}
