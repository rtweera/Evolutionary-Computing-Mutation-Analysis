# Evolutionary Computing Mutation Analysis

This project analyzes how different mutation-rate strategies affect convergence in a genetic algorithm using the [Jenetics](https://jenetics.io/) library.

## Overview

The program compares three mutation strategies on a bitstring optimization problem:

- **Baseline strategy**: constant low mutation rate.
- **High mutation at start**: higher mutation for early generations, then baseline.
- **High mutation at end**: baseline first, then higher mutation for late generations.

It reports:

- Final best and average fitness
- Convergence rate (solutions near optimal)
- Generations needed to reach optimal fitness

It also opens a Swing window with charts (JFreeChart) for:

- Final fitness distribution
- Average fitness over generations
- Fitness variation (standard deviation) over generations

## Tech Stack

- Java (configured for source/target **24** in `pom.xml`)
- Maven
- [Jenetics 7.2.0](https://mvnrepository.com/artifact/io.jenetics/jenetics)
- [JFreeChart 1.5.4](https://mvnrepository.com/artifact/org.jfree/jfreechart)

## Project Structure

```text
src/main/java/org/example/MutationRateAnalysis.java
pom.xml
```

Main entry point:

- `org.example.MutationRateAnalysis#main`

## Build and Run

From the project root:

```bash
mvn clean package
```

Run the application:

```bash
mvn exec:java -Dexec.mainClass="org.example.MutationRateAnalysis"
```

> Note: if `exec:java` is not available in your setup, run the compiled class from your IDE or configure the Maven Exec plugin.

## Notes

- The code currently expects Java 24 (`maven.compiler.source/target=24`).
- In environments without JDK 24, Maven compilation will fail with `invalid target release: 24`.
