/*  Copyright (c) 2013, Graeme Ball and Micron Oxford,                          
 *  University of Oxford, Department of Biochemistry.                           
 *                                                                               
 *  This program is free software: you can redistribute it and/or modify         
 *  it under the terms of the GNU General Public License as published by         
 *  the Free Software Foundation, either version 3 of the License, or            
 *  (at your option) any later version.                                          
 *                                                                               
 *  This program is distributed in the hope that it will be useful,              
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of               
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                
 *  GNU General Public License for more details.                                 
 *                                                                               
 *  You should have received a copy of the GNU General Public License            
 *  along with this program.  If not, see http://www.gnu.org/licenses/ .         
 */ 

package SIMcheck;
import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.GenericDialog; 

/** This plugin converts a SIM image to a pseudo-wide-field image by averaging
 * phases and angles. Assumes API OMX V2 CPZAT input channel order.
 * @author Graeme Ball <graemeball@gmail.com>
 **/ 
public class Util_SI2WF implements PlugIn {
    
    // parameter fields
    public int phases = 5;                                                         
    public int angles = 3;                                                         
    
    private static ImagePlus projImg = null;  // intermediate & final results

    @Override 
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        // TODO: option for padding to SIR result size for comparison
        GenericDialog gd = new GenericDialog("Raw SI data to Pseudo-Wide-Field");                   
        gd.addMessage("Requires SI raw data in API OMX (CPZAT) order.");        
        gd.addNumericField("Angles", angles, 1);                               
        gd.addNumericField("Phases", phases, 1);
        gd.showDialog();                                                        
        if (gd.wasCanceled()) return;                                           
        if(gd.wasOKed()){                                                     
            angles = (int)gd.getNextNumber();                                   
            phases = (int)gd.getNextNumber();                                   
        }                                                                       
        if (!I1l.stackDivisibleBy(imp, phases * angles)) {                                                 
            IJ.showMessage( "SI to Pseudo-Wide-Field", 
                    "Error: stack size not consistent with phases/angles.");
            return;                                                             
        }
        projImg = exec(imp, phases, angles);  
        projImg.show();
    }

    /** Execute plugin functionality: raw SI data to pseudo-widefield. 
     * @param imp input raw SI data ImagePlus                                   
     * @param phases number of phases                                   
     * @param angles number of angles                                   
     * @return ImagePlus with all phases and angles averaged
     */ 
    public ImagePlus exec(ImagePlus imp, int phases, int angles) {
        ImagePlus impCopy = imp.duplicate();
        this.angles = angles;
        this.phases = phases;
        int channels = imp.getNChannels();
        int Zplanes = imp.getNSlices();
        int frames = imp.getNFrames();
        Zplanes = Zplanes/(phases*angles);  // take phase & angle out of Z
        new StackConverter(impCopy).convertToGray32();  
        averagePandA(impCopy, channels, Zplanes, frames);
        I1l.copyCal(imp, projImg);
        projImg.setTitle(I1l.makeTitle(imp, "PWF"));  
        return projImg;
    }

    /** Average projections of 5 phases, 3 angles for each CZT **/
    private ImagePlus averagePandA(ImagePlus imp, int nc, int nz, int nt) {
        ImageStack stack = imp.getStack(); 
        ImageStack PAset = new ImageStack(imp.getWidth(), imp.getHeight());
        ImageStack avStack = new ImageStack(imp.getWidth(), imp.getHeight());
        int sliceIn = 0;
        int sliceOut = 0;
        // loop through in desired (PA)CZT output order, and project slices when we have a PA set
        int PAsetSize = phases * angles;
        IJ.showStatus("Averaging over phases and angles");
        for (int t = 1; t <= nt; t++) {
            for (int z = 1; z <= nz; z++) {
                IJ.showProgress(z, nz);
                for (int c = 1; c <= nc; c++) {
                    for (int a = 1; a <= angles; a++) {
                        for (int p = 1; p <= phases; p++) {
                            sliceIn = (t - 1) * (nc * phases * nz * angles);
                            sliceIn += (a - 1) * (nc * phases * nz);
                            sliceIn += (z - 1) * (nc * phases);
                            sliceIn += (p - 1) * nc;
                            sliceIn += c;
                            sliceOut++;
                            ImageProcessor ip = stack.getProcessor(sliceIn);
                            PAset.addSlice(null, stack.getProcessor(sliceIn));
                            if ((p * a == PAsetSize)) {
                                ip = avSlices(imp, PAset, PAsetSize);
                                for (int slice = PAsetSize; slice >= 1; slice--) {
                                    PAset.deleteSlice(slice);
                                }
                                sliceOut++;
                                avStack.addSlice(String.valueOf(sliceOut), ip);
                            }
                        }
                    }
                }
            }
        }
        projImg = new ImagePlus("projImg", avStack);
        projImg.setDimensions(nc, nz, nt);
        int centralZ = nz / 2;
        projImg.setZ(centralZ);
        projImg.setC(1);
        projImg.setT(1);
        projImg.setOpenAsHyperStack(true);
        return projImg;
    }

    /** Average slices (32-bit floats). */
    private ImageProcessor avSlices(
            ImagePlus imp, ImageStack stack, int slices) {
        int width = imp.getWidth();                                             
        int height = imp.getHeight();
        int len = width * height;
        FloatProcessor oip = new FloatProcessor(width, height);
        float[] avpixels = (float[])oip.getPixels();
        for (int slice = 1; slice <= slices; slice++){
            FloatProcessor fp = (FloatProcessor)stack.getProcessor(slice).convertToFloat();  
            float[] fpixels = (float[])fp.getPixels();
            for (int i = 0; i < len; i++) {
                avpixels[i] += fpixels[i];
            }
        }
        float fslices = (float)slices;                                                   
        for (int i = 0; i < len; i++) {                                            
            avpixels[i] /= fslices;
        }
        oip = new FloatProcessor(width, height, avpixels, null);
        return oip;
    }
}
