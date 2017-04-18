#!/usr/local/bin/Rscript 

# Execute with `Rscript create-charts.R`
# Reads perf/benchmarks.csv and generates two PNG charts into perf

library("ggplot2")

uniquefactor <- function(v) {
  factor(v, ordered=TRUE, levels=unique(v))
}

# Read the benchmarks in as 'b'

# Need to do some work here to convert the commit and kind columns into ordered factors.
# There's probably some mojo to do it all in the call to read.csv, but this works.

b <- read.csv("benchmarks.csv", stringsAsFactors=FALSE)
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

  cat("Saving chart as ", file_name, "\n", sep="")

  ggsave(file_name, parse.plot, width=10, height=7)
}

create_chart("parse-time.pdf", "Parse", b$parse)
create_chart("exec-time.pdf", "Execution", b$exec)
