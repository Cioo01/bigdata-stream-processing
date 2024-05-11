package com.example.bigdata;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.compiler.client.CompilerArguments;
import com.espertech.esper.compiler.client.EPCompileException;
import com.espertech.esper.compiler.client.EPCompiler;
import com.espertech.esper.compiler.client.EPCompilerProvider;
import com.espertech.esper.runtime.client.*;
import net.datafaker.Faker;
import net.datafaker.transformations.JsonTransformer;
import net.datafaker.transformations.Schema;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.lang.Math;

import static net.datafaker.transformations.Field.field;

public class EsperClient {
    public static void main(String[] args) throws InterruptedException {
        int noOfRecordsPerSec;
        int howLongInSec;
        if (args.length < 2) {
            noOfRecordsPerSec = 100;
            howLongInSec = 5;
        } else {
            noOfRecordsPerSec = Integer.parseInt(args[0]);
            howLongInSec = Integer.parseInt(args[1]);
        }

        Configuration config = new Configuration();
        EPCompiled epCompiled = getEPCompiled(config);

        // Connect to the EPRuntime server and deploy the statement
        EPRuntime runtime = EPRuntimeProvider.getRuntime("http://localhost:port", config);
        EPDeployment deployment;
        try {
            deployment = runtime.getDeploymentService().deploy(epCompiled);
        }
        catch (EPDeployException ex) {
            // handle exception here
            throw new RuntimeException(ex);
        }

        EPStatement resultStatement = runtime.getDeploymentService().getStatement(deployment.getDeploymentId(), "answer");
//        EPStatement inputStatement = runtime.getDeploymentService().getStatement(deployment.getDeploymentId(), "input");

        // Add a listener to the statement to handle incoming events
        resultStatement.addListener( (newData, oldData, stmt, runTime) -> {
            for (EventBean eventBean : newData) {
                System.out.printf("R: %s%n", eventBean.getUnderlying());
            }
        });

//        inputStatement.addListener( (newData, oldData, stmt, runTime) -> {
//            for (EventBean eventBean : newData) {
//                System.out.printf("I: %s%n", eventBean.getUnderlying());
//            }
//        });

        Faker faker = new Faker();
        String record;
        String[] colors = {"white", "black"};
        String[] titles = {"GM", "WGM", "FM", "CM", "IM", "WIM", "WFM", "WCM", "AGM", "AIM", "AFM", "ACM", "untitled"};
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() < startTime + (1000L * howLongInSec)) {
            for (int i = 0; i < noOfRecordsPerSec; i++) {
                String winner = faker.name().firstName() + " " + faker.name().lastName();
                String loser = faker.name().firstName() + " " + faker.name().lastName();
                while (loser.equals(winner)){
                    loser = faker.name().firstName() + " " + faker.name().lastName();
                }
                Timestamp ets = faker.date().past(60, TimeUnit.SECONDS);
                Timestamp its = new Timestamp(System.currentTimeMillis());
                String etsFormatted = dateFormat.format(ets);
                String itsFormatted = dateFormat.format(its);

                String finalLoser = loser;

                Schema<Object, ?> schema = Schema.of(
                        field("winner", () -> winner),
                        field("loser", () -> finalLoser),
                        field("winnersTitle", () -> titles[Math.abs(winner.hashCode() % 3)]),
                        field("losersTitle", () -> titles[Math.abs(finalLoser.hashCode() % 3)]),
                        field("winnersColor", () -> colors[(faker.number().numberBetween(0, 2))]),
                        field("opening", () -> faker.chess().opening()),
                        field("matchTimeInSeconds", () -> String.valueOf(faker.number().numberBetween(30, 1000))),
                        field("numberOfMoves", () -> String.valueOf(faker.number().numberBetween(10, 100))),
                        field("ets", () -> etsFormatted),
                        field("its", () -> itsFormatted)
                );

                JsonTransformer<Object> transformer = JsonTransformer.builder().build();
                record = transformer.generate(schema, 1);
                runtime.getEventService().sendEventJson(record, "ChessEvent");
            }
            waitToEpoch();
        }
    }

    private static EPCompiled getEPCompiled(Configuration config) {
        CompilerArguments compilerArgs = new CompilerArguments(config);

        // Compile the EPL statement
        EPCompiler compiler = EPCompilerProvider.getCompiler();
        EPCompiled epCompiled;
        try {
            epCompiled = compiler.compile("""
                    @public @buseventtype create json schema ChessEvent(winner string, loser string, winnersTitle string, losersTitle string, winnersColor string, opening string, matchTimeInSeconds int, numberOfMoves int, ets string, its string);
                    
                     create window GMOpeningsWindow#length(10) as (opening string, match_count long);
                     create window WGMOpeningsWindow#length(10) as (opening string, match_count long);
                                        
                     INSERT INTO GMOpeningsWindow(opening, match_count)
                     SELECT opening, count(opening) from ChessEvent#ext_timed(java.sql.Timestamp.valueOf(its).getTime(), 60 sec)
                     WHERE winnersTitle = 'GM'
                     GROUP BY opening
                     ORDER BY count(opening) desc
                     LIMIT 10;
          
                     INSERT INTO WGMOpeningsWindow(opening, match_count)
                     SELECT opening, count(opening) from ChessEvent#ext_timed(java.sql.Timestamp.valueOf(its).getTime(), 60 sec)
                     WHERE winnersTitle = 'WGM'
                     GROUP BY opening
                     ORDER BY count(opening) desc
                     LIMIT 10;
                    
                     @name('answer') select coalesce(wgm.opening, gm.opening) as opening, coalesce(gm.match_count,0) as gamesGM, coalesce(wgm.match_count,0) as gamesWGM
                     from GMOpeningsWindow as gm
                     full outer join
                     WGMOpeningsWindow as wgm on gm.opening = wgm.opening
                     group by gm.opening;
                   
                    """, compilerArgs);
        }
        catch (EPCompileException ex) {
            // handle exception here
            throw new RuntimeException(ex);
        }
        return epCompiled;
    }

    static void waitToEpoch() throws InterruptedException {
        long millis = System.currentTimeMillis();
        Instant instant = Instant.ofEpochMilli(millis) ;
        Instant instantTrunc = instant.truncatedTo( ChronoUnit.SECONDS ) ;
        long millis2 = instantTrunc.toEpochMilli() ;
        TimeUnit.MILLISECONDS.sleep(millis2+1000-millis);
    }
}

