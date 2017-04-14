#!/usr/local/bin/Rscript 

# Execute with `Rscript create-charts.R`
# Reads perf/benchmarks.csv and generates two PNG charts into perf

# Read the benchmarks in as 'b'

library("ggplot2")

uniquefactor <- function(v) {
  factor(v, ordered=TRUE, levels=unique(v))
}

# Need to do some work here to convert the commit and kind columns into ordered factors.
b <- read.csv("perf/benchmarks.csv", stringsAsFactors=FALSE)
b$date = factor(b$date, ordered=TRUE)
b$commit = uniquefactor(b$commit)
b$kind = factor(b$kind, ordered=TRUE)

create_chart <- function(file_name, type_name, data) {
  parse.plot <-
    qplot(b$commit, data, color=b$kind, shape=b$kind, group=b$kind) +
      geom_point(size=4) +
      labs(x="Commit",
      y=paste(type_name ,"Time (ms)"),
      title=paste(type_name, "Time Benchmarks"),
      color="Benchmark",
      shape="Benchmark") +
      geom_path() +
      theme(plot.title = element_text(size=18),
        axis.text.x=element_text(angle=45, hjust=1))

  cat("Saving chart as ", file_name, "\n")

  ggsave(file_name, parse.plot)
}

create_chart("perf/parse-time.png", "Parse", b$parse)
create_chart("perf/exec-time.png", "Execution", b$exec)
