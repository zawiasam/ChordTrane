package models

import models.ChordGenerator._


case class BassSettings(lower: Int, upper: Int, stayInTessitura: Double, connectivity: Double)
case class SingleNote(note: Int, tick: Int)


object BassPlayer{

	val DefaultBassSettings = BassSettings(Note.getMidiNote("G", 1), Note.getMidiNote("G", 3), 3, 2)
	val DefaultCenter = (DefaultBassSettings.lower + DefaultBassSettings.upper) / 2
	val DefaultDeviation = DefaultBassSettings.upper - DefaultCenter



	
	
	def getBass(chordTemplate: Array[ComperTemplate], bassSettings: BassSettings) = {

		val center = (bassSettings.upper + bassSettings.lower) / 2
		val deviation = bassSettings.upper - center

		def bassRangeProbability = Helper.powerProbabilityCurve(center, deviation, bassSettings.stayInTessitura)
		def octaveShiftNecessary(note: Int) = Helper.rollDice(bassRangeProbability(note))



		def nextBassRoot(previousRoot: Int, desiredRoot: Int) = {
			val closestInterval = Note.closestInterval(previousRoot, desiredRoot)

			val interval = closestInterval match {
				case 5 => {//Perfect 4th up
					if(Helper.rollDice(0.5)){//50% of time descend 5th instead of ascend 4th
						-Interval.P5
					}
					else{
						Interval.P4
					}
				}

				case -5 => {//Perfect 4th down
					if(Helper.rollDice(0.5)){//50% of time ascend 5th instead of descend 4th
						Interval.P5
					}
					else{
						-Interval.P4
					}
				}
				case 6 | -6 => {//Tritone
					if(Helper.rollDice(0.5)){//50% up, 50% down
						Interval.A4
					}
					else{
						-Interval.A4
					}
				}

				case i => i
			}


			val potentialNextNote = previousRoot + interval

			//If note out of range, make it in range. Based on probability
			if(potentialNextNote > DefaultCenter){//If higher than center
				if(octaveShiftNecessary(potentialNextNote)){
					potentialNextNote - 12
				}else{
					potentialNextNote
				}
			}
			else{//If lower than center
				if(octaveShiftNecessary(potentialNextNote)){
					potentialNextNote + 12
				}else{
					potentialNextNote
				}
			}
		}

		def bassSkeleton(chordTemplate: Array[ComperTemplate]) = {

			def loop(chordQueue: Array[ComperTemplate], out: Array[SingleNote]): Array[SingleNote] = {

				if(chordQueue.isEmpty){
					out
				}else{

					val previousNote = out.last.note

					val nextChordRoot = chordQueue.head.chordGenerator.rootMidi

					val nextNote = nextBassRoot(previousNote, nextChordRoot)

					//println(nextNote)

					val finalOut = SingleNote(nextNote, chordQueue.head.tick)//keep same tick

					loop(chordQueue.tail, out :+ finalOut)

				}
			}

			val firstChord = chordTemplate.head

			def initialNote(note: Int) = {//Get first note
				val desiredHeight = Helper.getRandomWithinRange(bassSettings.lower, bassSettings.upper)
				val octaveAdjust = Note.octaveAdjust(note, desiredHeight)

				note + octaveAdjust
			}

			val firstNote = SingleNote(initialNote(firstChord.chordGenerator.rootMidi), firstChord.tick)
			
			loop(chordTemplate.tail, Array[SingleNote](firstNote))
		}

		
		
		val skeleton = bassSkeleton(chordTemplate)

		//println(Note.printNotes(skeleton.map(_.note)))
		
		val notesWithChords = skeleton zip chordTemplate.map(_.chordGenerator)
		
		def loop(current: Tuple2[SingleNote, ChordGenerator], queue: Array[Tuple2[SingleNote, ChordGenerator]], out: Array[SingleNote]): Array[SingleNote] = {
			
			if(queue.isEmpty){
				out 
			}else{
				val currentNote = current._1
				val currentChord = current._2
				val nextNote = queue.head._1
				val inbetweenNotes = fillInBassNotes(currentNote, nextNote, currentChord)

				loop(queue.head, queue.tail, (out :+ currentNote) ++ inbetweenNotes)
			}
		}


		


		loop(notesWithChords.head, notesWithChords.tail, Array())
		
	}
	

	

	


	

	def chromaticDiatonicApproach(destination: SingleNote) = {
		if(Helper.rollDice(0.95)){//Chromatic
			val noteInBetween  = if(Helper.rollDice(0.5)){
				destination.note - 1 //approach from below
			}else{
				destination.note + 1 //approach from above
			}

			SingleNote(noteInBetween, destination.tick - 2)

		}else{//Whole step
			val noteInBetween  = if(Helper.rollDice(0.5)){
				destination.note - 2 //approach from below
			}else{
				destination.note + 2 //approach from above
			}

			SingleNote(noteInBetween, destination.tick - 2)
		}
	}

	//2 ticks per quarter
	def fillInBassNotes(begin: SingleNote, end: SingleNote, chordGenerator: ChordGenerator): Array[SingleNote] = {
		val beatsInBetween = (end.tick - begin.tick)/2 - 1

		beatsInBetween match {
			case 0 => {
				Array()
			}
			case 1 => {//One note between
				Array(chromaticDiatonicApproach(end))
			}
			case 2 => {
				val chordTones = Note.chordTonesInBetweenInclusive(begin.note, end.note, chordGenerator.chordTones)

				val firstPitch = Helper.getRandomFromArray[Int](chordTones)
				val firstNote = SingleNote(firstPitch, begin.tick + 2)
				val secondNote = chromaticDiatonicApproach(end)

				Array(firstNote, secondNote)
			}
			case 3 => {
				//Let's say going from D3 to G3
				//Separation is small, so let chord tones possible be between D3 and D4
				val chordTones = if(end.note == begin.note){//if destination higher than beginning
					Note.chordTonesInBetweenInclusive(begin.note - 12, begin.note + 12, chordGenerator.chordTones)
				}else if(end.note > begin.note){
					Note.chordTonesInBetweenInclusive(begin.note, begin.note + 12, chordGenerator.chordTones)
				}else{
					Note.chordTonesInBetweenInclusive(begin.note, begin.note - 12, chordGenerator.chordTones)
				}

				// val chordTones = Note.chordTonesInBetweenInclusive(begin.note, end.note, chordGenerator.chordTones)

				val secondPitch = Helper.getRandomFromArray[Int](chordTones)

				val secondNote = SingleNote(secondPitch, begin.tick + 4)

				val firstNote = chromaticDiatonicApproach(secondNote)

				val thirdNote = chromaticDiatonicApproach(end)

				Array(firstNote, secondNote, thirdNote)

			}
			case n => {
				(1 to n).map{beat => SingleNote(begin.note, begin.tick + 2 * beat)}.toArray		
			} 
		}
	}
}