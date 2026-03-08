#ifndef GAMMA_GAMMA_H_INC
#define GAMMA_GAMMA_H_INC

/*	Gamma - Generic processing library
	See COPYRIGHT file for authors and license information

	File Description:
	Main Gamma includes
*/

/*! \mainpage Gamma - Generic synthesis library

	\section intro_sec About

	Gamma is a cross-platform, C++ library for doing generic synthesis and 
	filtering of signals. It contains helpful mathematical functions, 
	types, such as vectors and complex numbers, an assortment of sequence 
	generators, and many other objects for signal processing tasks. 
	It is oriented towards real-time sound and graphics synthesis, but is 
	equally useful for non-real-time tasks.
*/

#define GAMMA_VERSION "0.9.8x"
//#define GAMMA_H_INC_ALL

// Core Functions
// Everything else depends on these so always include them.
#include "Containers.h"
#include "Strategy.h"
#include "Types.h"

#include "arr.h"
#include "gen.h"
#include "ipl.h"
#include "mem.h"
#include "scl.h"
#include "tbl.h"
#include "rnd.h"

// Optional includes
#ifdef GAMMA_H_INC_ALL

	// System/Utility
	#include "AudioIO.h"
	#include "Conversion.h"
	#include "Print.h"
	#include "TransferFunc.h"

	// Generators/Filters
	#include "Access.h"
	#include "Delay.h"
	#include "DFT.h"
	#include "Domain.h"
	#include "Envelope.h"
	#include "FFT.h"
	#include "Filter.h"
	#include "FormantData.h"
	#include "Noise.h"
	#include "Oscillator.h"
	#include "Ramped.h"
	#include "Recorder.h"
	#include "SamplePlayer.h"
	#include "Spatial.h"
	#include "SoundFile.h"
	#include "UnitMaps.h"

	// Composite Objects
	#include "Analysis.h"
	#include "Effects.h"

	// Scheduling/Timing
	#include "Scheduler.h"
	#include "Timer.h"
	#include "Voices.h"

#endif

#endif

