#!/usr/bin/env python3
"""Plot input vs output signal waveforms from JNI filter tests.

Reads .raw files (32-bit float, little-endian) exported by GammaTest and
generates an interactive Bokeh HTML page. Each filter gets its own plot with
the input and output waveforms overlaid for direct comparison.

Usage:
    # Plot all filters found in the directory
    python tools/plot_signals.py test-signals/

    # Plot a single filter
    python tools/plot_signals.py test-signals/bandPassFilter

    # Customize the number of samples displayed (default: 2000)
    python tools/plot_signals.py test-signals/ --samples 5000

    # Show all samples
    python tools/plot_signals.py test-signals/ --samples 0

Pull signal files from the emulator first:
    adb pull /storage/emulated/0/Download/test-signals/ ./test-signals/
"""
import argparse
import struct
import sys
from pathlib import Path

from bokeh.plotting import figure, output_file, save
from bokeh.layouts import column
from bokeh.models import Legend, LegendItem

SAMPLE_RATE = 96000


def load_raw_signal(filepath: Path) -> list[float]:
    """Load a raw 32-bit IEEE 754 float signal file (little-endian)."""
    data = filepath.read_bytes()
    sample_count = len(data) // 4
    return list(struct.unpack(f"<{sample_count}f", data))


def compute_rms(signal: list[float]) -> float:
    """Compute RMS energy of a signal."""
    if not signal:
        return 0.0
    sum_squares = sum(s * s for s in signal)
    return (sum_squares / len(signal)) ** 0.5


def plot_filter_test(
    input_signal: list[float],
    output_signal: list[float],
    filter_name: str,
    display_samples: int,
) -> figure:
    """Create a Bokeh figure with input and output waveforms overlaid."""
    if display_samples <= 0:
        display_samples = len(input_signal)
    else:
        display_samples = min(display_samples, len(input_signal))

    # Time axis in milliseconds
    time_axis = [i / SAMPLE_RATE * 1000 for i in range(display_samples)]

    input_rms = compute_rms(input_signal)
    output_rms = compute_rms(output_signal)
    rms_change = ((output_rms - input_rms) / input_rms * 100) if input_rms > 0 else 0

    title = (
        f"{filter_name}  —  "
        f"Input RMS: {input_rms:.4f}  |  "
        f"Output RMS: {output_rms:.4f}  |  "
        f"Change: {rms_change:+.1f}%"
    )

    fig = figure(
        title=title,
        x_axis_label="Time (ms)",
        y_axis_label="Amplitude",
        width=1200,
        height=350,
        tools="pan,wheel_zoom,box_zoom,reset,save",
        active_drag="pan",
        active_scroll="wheel_zoom",
    )

    input_line = fig.line(
        time_axis,
        input_signal[:display_samples],
        color="steelblue",
        alpha=0.7,
        line_width=1,
    )

    output_line = fig.line(
        time_axis,
        output_signal[:display_samples],
        color="coral",
        alpha=0.8,
        line_width=1,
    )

    legend = Legend(
        items=[
            LegendItem(label="Input", renderers=[input_line]),
            LegendItem(label="Output", renderers=[output_line]),
        ],
        location="top_right",
        click_policy="hide",
    )
    fig.add_layout(legend)

    return fig


def main():
    parser = argparse.ArgumentParser(
        description="Plot input vs output signal waveforms from JNI filter tests."
    )
    parser.add_argument(
        "path",
        help="Directory containing .raw files, or a filter name prefix "
        "(e.g., test-signals/bandPassFilter)",
    )
    parser.add_argument(
        "--samples",
        type=int,
        default=2000,
        help="Number of samples to display per plot (0 = all). Default: 2000",
    )
    parser.add_argument(
        "--output",
        type=str,
        default=None,
        help="Output HTML file path. Default: <signal_dir>/filter_plots.html",
    )
    args = parser.parse_args()

    path = Path(args.path)

    # Determine if path is a directory or a filter name prefix
    if path.is_dir():
        signal_dir = path
        input_files = sorted(signal_dir.glob("*_input.raw"))
    else:
        # Treat as a prefix — e.g., "test-signals/bandPassFilter"
        signal_dir = path.parent
        prefix = path.name
        input_files = sorted(signal_dir.glob(f"{prefix}*_input.raw"))

    if not input_files:
        print(f"No *_input.raw files found at {args.path}")
        sys.exit(1)

    figures = []
    for input_file in input_files:
        filter_name = input_file.stem.replace("_input", "")
        output_file_path = input_file.with_name(f"{filter_name}_output.raw")

        if not output_file_path.exists():
            print(f"Warning: no output file for {filter_name}, skipping")
            continue

        input_signal = load_raw_signal(input_file)
        output_signal = load_raw_signal(output_file_path)

        fig = plot_filter_test(
            input_signal, output_signal, filter_name, args.samples
        )
        figures.append(fig)
        print(f"  {filter_name}: {len(input_signal)} samples")

    if not figures:
        print("No filter pairs found to plot.")
        sys.exit(1)

    html_path = Path(args.output) if args.output else signal_dir / "filter_plots.html"
    output_file(str(html_path), title="Gamma Filter Test Results")
    save(column(*figures, sizing_mode="stretch_width"))

    print(f"\nSaved interactive plot with {len(figures)} filters to {html_path}")


if __name__ == "__main__":
    main()
