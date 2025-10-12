package org.example;

import io.jenetics.*;
import io.jenetics.engine.*;
import io.jenetics.util.Seq;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class MutationRateAnalysis {

    private static final int CHROMOSOME_LENGTH = 10;
    private static final int POPULATION_SIZE = 1000;
    private static final int MAX_GENERATIONS = 100;
    private static final double OPTIMAL_FITNESS = 10.0;
    private static final int THRESHOLD_FITNESS = 9; // To consider as converging

    private static final int TOURNAMENT_SELECTION_SIZE = 2;
    private static final double CROSSOVER_PROBABILITY = 0.01;
    private static final int INITIAL_MUTATION_END = 20;
    private static final int ENDING_MUTATION_START = 90;
    private static final double INITIAL_MUTATION_PROB = 0.4;
    private static final double ENDING_MUTATION_PROB = 0.4;
    private static final double DEFAULT_MUTATION_PROB = 0.1;

    private static final Paint BASE_COLOR = Color.BLACK;
    private static final Paint INITIAL_MUTATION_COLOR = Color.BLUE;
    private static final Paint ENDING_MUTATION_COLOR = Color.RED;
    private static final Stroke THICK_STROKE = new BasicStroke(4.0f);


    /**
     * Calculates the fitness of a given genotype by counting the number of '1' bits
     * in its chromosome. This fitness function is used to evaluate how well a genotype
     * performs in the genetic algorithm.
     *
     * @param gt the genotype to evaluate, which contains a chromosome of `BitGene` objects
     * @return the fitness value, represented as the count of '1' bits in the chromosome
     */
    private static int fitness(Genotype<BitGene> gt) {
        return (int) gt.chromosome().stream()
                .filter(BitGene::bit)
                .count();
    }

    /**
     * A custom mutator that dynamically adjusts the mutation probability
     * based on the current generation of the genetic algorithm. This allows
     * for adaptive mutation rates during the evolution process.
     */
    private static class AdaptiveMutator extends Mutator<BitGene, Integer> {
        private final MutationStrategy strategy;

        /**
         * Constructs an AdaptiveMutator with a given mutation strategy.
         *
         * @param strategy the mutation strategy that determines the mutation
         *                 probability for each generation
         */
        public AdaptiveMutator(MutationStrategy strategy) {
            super(0.01); // Default low mutation
            this.strategy = strategy;
        }

        /**
         * Alters the population by applying mutation with a dynamically
         * determined probability based on the current generation.
         *
         * @param population the population of phenotypes to be altered
         * @param generation the current generation number
         * @return the result of the alteration, including the altered population
         */
        @Override
        public AltererResult<BitGene, Integer> alter(Seq<Phenotype<BitGene, Integer>> population, long generation) {
            int currentGeneration = (int) generation;
            double mutationProb = strategy.getMutationProbability(currentGeneration);

            // Create a new mutator with the appropriate probability
            Mutator<BitGene, Integer> mutator = new Mutator<>(mutationProb);
            return mutator.alter(population, generation);
        }
    }

    /**
     * Interface representing a mutation strategy for a genetic algorithm.
     * Provides methods to determine the mutation probability for a given generation
     * and to retrieve the name of the strategy.
     */
    interface MutationStrategy {

        /**
         * Calculates the mutation probability for a specific generation.
         *
         * @param generation the current generation number
         * @return the mutation probability as a double value
         */
        double getMutationProbability(int generation);

        /**
         * Retrieves the name of the mutation strategy.
         *
         * @return the name of the strategy as a String
         */
        String getName();
    }

    // Baseline: constant low mutation rate
    static class BaselineStrategy implements MutationStrategy {
        @Override
        public double getMutationProbability(int generation) {
            return DEFAULT_MUTATION_PROB;
        }

        @Override
        public String getName() {
            return String.format("Baseline (Constant %.0f%%) mutation probability", DEFAULT_MUTATION_PROB * 100);
        }
    }

    // High mutation at start
    static class HighMutationStartStrategy implements MutationStrategy {
        @Override
        public double getMutationProbability(int generation) {
            if (generation <= INITIAL_MUTATION_END) {
                return INITIAL_MUTATION_PROB;
            }
            return DEFAULT_MUTATION_PROB;
        }

        @Override
        public String getName() {
            return String.format("High Mutation Start (%.0f%% mutation probability for gen 1-%d)", INITIAL_MUTATION_PROB * 100, INITIAL_MUTATION_END);
        }
    }

    // High mutation at end
    static class HighMutationEndStrategy implements MutationStrategy {
        @Override
        public double getMutationProbability(int generation) {
            if (generation > ENDING_MUTATION_START) {
                return ENDING_MUTATION_PROB;
            }
            return DEFAULT_MUTATION_PROB;
        }

        @Override
        public String getName() {
            return String.format("High Mutation End (%.0f%% mutation probability for gen %d-%d)", ENDING_MUTATION_PROB * 100, ENDING_MUTATION_START, MAX_GENERATIONS);
        }
    }

    /**
     * Container class for storing the results of a genetic algorithm simulation.
     * This class holds various metrics and data collected during the simulation,
     * such as fitness values, convergence rates, and the number of generations
     * required to reach the optimal solution.
     */
    static class SimulationResult {
        /** The name of the mutation strategy used in the simulation. */
        String strategyName;

        /** A list of the best fitness values recorded for each generation. */
        List<Double> bestFitnessPerGen = new ArrayList<>();

        /** A list of the average fitness values recorded for each generation. */
        List<Double> avgFitnessPerGen = new ArrayList<>();

        /** A list of the fitness values of the final population. */
        List<Integer> finalPopulationFitness = new ArrayList<>();

        /** The best fitness value achieved in the final generation. */
        double finalBestFitness;

        /** The average fitness value of the final population. */
        double finalAvgFitness;

        /** The convergence rate, representing the proportion of solutions near optimal. */
        double convergenceRate;

        /** The number of generations required to reach the optimal solution. */
        int generationsToOptimal;

        /** A list of fitness values for all individuals in each generation. */
        List<List<Integer>> fitnessPerGenAllIndividuals = new ArrayList<>();

        public void printMetrics() {
            System.out.println("\n=== " + strategyName + " ===");
            System.out.println("Final Best Fitness: " + finalBestFitness);
            System.out.println("Final Average Fitness: " + String.format("%.2f", finalAvgFitness));
            System.out.println("Convergence Rate: " + String.format("%.2f%%", convergenceRate * 100));
            System.out.println("Generations to Optimal: " +
                    (generationsToOptimal == -1 ? "Never reached" : generationsToOptimal));
            System.out.println("Solutions at Optimal (10): " +
                    finalPopulationFitness.stream().filter(f -> f == 10).count());
            System.out.println("Solutions >= " + THRESHOLD_FITNESS + ": " +
                    finalPopulationFitness.stream().filter(f -> f >= 8).count());
        }
    }

    /**
     * Runs a genetic algorithm simulation using the specified mutation strategy.
     * This method initializes the genetic algorithm engine, tracks the evolution
     * process, and collects various metrics about the simulation results.
     *
     * @param strategy the mutation strategy to be used in the simulation
     * @return a `SimulationResult` object containing the metrics and data
     *         collected during the simulation
     */
    private static SimulationResult runSimulation(MutationStrategy strategy) {
        /*
         * NOTE:
         * - simulationResult is the one we created above to store results
         * - finalResult is from jenetics library to store the final result of the evolution
         */
        SimulationResult simulationResult = new SimulationResult();
        simulationResult.strategyName = strategy.getName();

        // Create the genetic algorithm engine
        Engine<BitGene, Integer> engine = Engine
                .builder(MutationRateAnalysis::fitness, BitChromosome.of(CHROMOSOME_LENGTH))
                .populationSize(POPULATION_SIZE)
                .alterers(new AdaptiveMutator(strategy), new SinglePointCrossover<>(CROSSOVER_PROBABILITY))
                .selector(new TournamentSelector<>(TOURNAMENT_SELECTION_SIZE))
                .maximizing()
                .build();

        // Track when optimal is first reached
        simulationResult.generationsToOptimal = -1;

        // Store reference to the actual final evolution result
        final EvolutionResult<BitGene, Integer>[] lastResult = new EvolutionResult[1];

        // Evolution statistics
        EvolutionResult<BitGene, Integer> finalResult = engine.stream()
                .limit(MAX_GENERATIONS)
                .peek(er -> {
                    // Track best and average fitness
                    simulationResult.bestFitnessPerGen.add((double) er.bestFitness());
                    simulationResult.avgFitnessPerGen.add(er.population().stream()
                            .mapToInt(Phenotype::fitness)
                            .average()
                            .orElse(0.0));

                    // Store all fitness values for variation calculation
                    List<Integer> allFitness = er.population().stream()
                            .map(Phenotype::fitness)
                            .collect(Collectors.toList());
                    simulationResult.fitnessPerGenAllIndividuals.add(allFitness);

                    // Check if optimal reached
                    if (simulationResult.generationsToOptimal == -1 && er.bestFitness() == OPTIMAL_FITNESS) {
                        simulationResult.generationsToOptimal = (int) er.generation();
                    }
                    // Store the last result (generation 100)
                    lastResult[0] = er;
                })
                .collect(EvolutionResult.toBestEvolutionResult());

        // Collect final population fitness from the ACTUAL last generation
        simulationResult.finalPopulationFitness = lastResult[0].population().stream()
                .map(Phenotype::fitness)
                .collect(Collectors.toList());

        simulationResult.finalBestFitness = lastResult[0].bestFitness();
        simulationResult.finalAvgFitness = simulationResult.finalPopulationFitness.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        // Calculate convergence rate (how many solutions are near optimal)
        long nearOptimal = simulationResult.finalPopulationFitness.stream()
                .filter(fitnessValue -> fitnessValue >= THRESHOLD_FITNESS)
                .count();
        simulationResult.convergenceRate = (double) nearOptimal / POPULATION_SIZE;

        return simulationResult;
    }

    // Create histogram chart
    private static JPanel createHistogram(Map<String, SimulationResult> results) {
        // Use CategoryDataset instead of HistogramDataset
        org.jfree.data.category.DefaultCategoryDataset dataset =
                new org.jfree.data.category.DefaultCategoryDataset();

        // Count frequencies for each fitness value (0-10) for each strategy
        for (Map.Entry<String, SimulationResult> entry : results.entrySet()) {
            String strategyName = entry.getKey();
            Map<Integer, Long> frequencyMap = entry.getValue().finalPopulationFitness.stream()
                    .collect(Collectors.groupingBy(f -> f, Collectors.counting()));

            // Add all fitness values 0-10
            for (int fitness = 0; fitness <= 10; fitness++) {
                long count = frequencyMap.getOrDefault(fitness, 0L);
                dataset.addValue(count, strategyName, String.valueOf(fitness));
            }
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "Final Population Fitness Distribution",
                "Fitness Value",
                "Frequency",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        org.jfree.chart.renderer.category.BarRenderer renderer =
                (org.jfree.chart.renderer.category.BarRenderer) chart.getCategoryPlot().getRenderer();
        renderer.setSeriesPaint(0, BASE_COLOR);
        renderer.setSeriesPaint(1, INITIAL_MUTATION_COLOR);
        renderer.setSeriesPaint(2, ENDING_MUTATION_COLOR);

        return new ChartPanel(chart);
    }

    // Create average fitness evolution chart
    private static JPanel createFitnessEvolutionChart(Map<String, SimulationResult> results) {
        XYSeriesCollection dataset = new XYSeriesCollection();

        for (Map.Entry<String, SimulationResult> entry : results.entrySet()) {
            XYSeries series = new XYSeries(entry.getKey());
            SimulationResult result = entry.getValue();
            for (int i = 0; i < result.avgFitnessPerGen.size(); i++) {
                series.add(i, result.avgFitnessPerGen.get(i));
            }
            dataset.addSeries(series);
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Average Fitness Over Generations",
                "Generation",
                "Average Fitness",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        // Color change for better visibility
        chart.getXYPlot().getRenderer().setSeriesPaint(0, BASE_COLOR);
        chart.getXYPlot().getRenderer().setSeriesPaint(1, INITIAL_MUTATION_COLOR);
        chart.getXYPlot().getRenderer().setSeriesPaint(2, ENDING_MUTATION_COLOR); // Changed from green

        // Set line width (2.0f is default, try 3.0f or 4.0f for thicker lines)
        chart.getXYPlot().getRenderer().setSeriesStroke(0, THICK_STROKE);
        chart.getXYPlot().getRenderer().setSeriesStroke(1, THICK_STROKE);
        chart.getXYPlot().getRenderer().setSeriesStroke(2, THICK_STROKE);

        return new ChartPanel(chart);
    }

    // Create fitness variation (standard deviation) evolution chart
    private static JPanel createFitnessVariationChart(Map<String, SimulationResult> results) {
        XYSeriesCollection dataset = new XYSeriesCollection();

        for (Map.Entry<String, SimulationResult> entry : results.entrySet()) {
            XYSeries series = new XYSeries(entry.getKey());
            SimulationResult result = entry.getValue();

            // Calculate standard deviation for each generation
            for (int i = 0; i < result.fitnessPerGenAllIndividuals.size(); i++) {
                List<Integer> genFitness = result.fitnessPerGenAllIndividuals.get(i);
                double mean = genFitness.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                double variance = genFitness.stream()
                        .mapToDouble(f -> Math.pow(f - mean, 2))
                        .average()
                        .orElse(0.0);
                double stdDev = Math.sqrt(variance);
                series.add(i, stdDev);
            }
            dataset.addSeries(series);
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Fitness Variation (Std Dev) Over Generations",
                "Generation",
                "Standard Deviation",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        // Color change for better visibility
        chart.getXYPlot().getRenderer().setSeriesPaint(0, BASE_COLOR);
        chart.getXYPlot().getRenderer().setSeriesPaint(1, INITIAL_MUTATION_COLOR);
        chart.getXYPlot().getRenderer().setSeriesPaint(2, ENDING_MUTATION_COLOR); // Changed from green

        // Set line width (2.0f is default, try 3.0f or 4.0f for thicker lines)
        chart.getXYPlot().getRenderer().setSeriesStroke(0, THICK_STROKE);
        chart.getXYPlot().getRenderer().setSeriesStroke(1, THICK_STROKE);
        chart.getXYPlot().getRenderer().setSeriesStroke(2, THICK_STROKE);

        return new ChartPanel(chart);
    }

    // Create the best fitness evolution chart
    private static JPanel createBestFitnessChart(Map<String, SimulationResult> results) {
        XYSeriesCollection dataset = new XYSeriesCollection();

        for (Map.Entry<String, SimulationResult> entry : results.entrySet()) {
            XYSeries series = new XYSeries(entry.getKey());
            SimulationResult result = entry.getValue();
            for (int i = 0; i < result.bestFitnessPerGen.size(); i++) {
                series.add(i, result.bestFitnessPerGen.get(i));
            }
            dataset.addSeries(series);
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Best Fitness Over Generations",
                "Generation",
                "Best Fitness",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        return new ChartPanel(chart);
    }

    public static void main(String[] args) {
        System.out.println("Starting Genetic Algorithm Mutation Rate Analysis");
        System.out.println("Chromosome Length: " + CHROMOSOME_LENGTH);
        System.out.println("Population Size: " + POPULATION_SIZE);
        System.out.println("Max Generations: " + MAX_GENERATIONS);
        System.out.println("Optimal Fitness: " + OPTIMAL_FITNESS);

        // Define strategies
        List<MutationStrategy> strategies = Arrays.asList(
                new BaselineStrategy(),
                new HighMutationStartStrategy(),
                new HighMutationEndStrategy()
        );

        // Run simulations
        Map<String, SimulationResult> results = new LinkedHashMap<>();
        for (MutationStrategy strategy : strategies) {
            System.out.println("\nRunning: " + strategy.getName());
            SimulationResult result = runSimulation(strategy);
            results.put(strategy.getName(), result);
            result.printMetrics();
        }

        // Create GUI with charts
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Genetic Algorithm Mutation Analysis");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new GridLayout(2, 2, 20, 20));

            frame.add(createHistogram(results));
            frame.add(createFitnessEvolutionChart(results));
//            frame.add(createBestFitnessChart(results));
            frame.add(createFitnessVariationChart(results));

            // Add metrics panel
            JPanel metricsPanel = new JPanel();
            metricsPanel.setLayout(new BoxLayout(metricsPanel, BoxLayout.Y_AXIS));
            JTextArea metricsText = new JTextArea(20, 40);
            metricsText.setEditable(false);
            StringBuilder sb = new StringBuilder();
            for (SimulationResult result : results.values()) {
                sb.append("\n**").append(result.strategyName).append("**\n");
                sb.append("Final Best: ").append(result.finalBestFitness).append("\n");
                sb.append("Final Avg: ").append(String.format("%.2f", result.finalAvgFitness)).append("\n");
                sb.append("Convergence: ").append(String.format("%.2f%%", result.convergenceRate * 100)).append("\n");
                sb.append("Generations to Optimal: ").append(result.generationsToOptimal == -1 ? "Never" : result.generationsToOptimal).append("\n");
                sb.append("Solutions at Optimal: ").append(result.finalPopulationFitness.stream().filter(f -> f == 10).count()).append("\n");
                sb.append("---\n");
            }
            sb.append("\nNOTE: Convergence is percent of individuals in the final population with fitness >= ").append(THRESHOLD_FITNESS).append("\n");
            metricsText.setText(sb.toString());
            metricsPanel.add(new JScrollPane(metricsText));
            frame.add(metricsPanel);

            frame.setSize(1400, 1000);
            frame.setVisible(true);
        });
    }
}