// Render the repository's existing shield/check icon as an opaque iOS app icon.
import AppKit
import Foundation

let side = 1024
let colorSpace = CGColorSpaceCreateDeviceRGB()
let context = CGContext(data: nil, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4,
                        space: colorSpace, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
context.setFillColor(CGColor(red: 22/255, green: 75/255, blue: 67/255, alpha: 1))
context.fill(CGRect(x: 0, y: 0, width: side, height: side))
context.translateBy(x: 0, y: CGFloat(side)); context.scaleBy(x: 8, y: -8)
context.setLineWidth(7); context.setLineJoin(.round); context.setLineCap(.round)
context.setStrokeColor(CGColor(gray: 1, alpha: 1))
context.move(to: CGPoint(x: 64,y: 24)); context.addLine(to: CGPoint(x: 93,y: 35)); context.addLine(to: CGPoint(x: 93,y: 60))
context.addCurve(to: CGPoint(x: 64,y: 99), control1: CGPoint(x: 93,y: 82), control2: CGPoint(x: 64,y: 99))
context.addCurve(to: CGPoint(x: 35,y: 60), control1: CGPoint(x: 64,y: 99), control2: CGPoint(x: 35,y: 82))
context.addLine(to: CGPoint(x: 35,y: 35)); context.addLine(to: CGPoint(x: 64,y: 24)); context.closePath(); context.strokePath()
context.setStrokeColor(CGColor(red: 169/255,green: 226/255,blue: 196/255,alpha: 1))
context.move(to: CGPoint(x: 50,y: 63)); context.addLine(to: CGPoint(x: 59,y: 72)); context.addLine(to: CGPoint(x: 79,y: 50)); context.strokePath()
let bitmap = NSBitmapImageRep(cgImage: context.makeImage()!)
let data = bitmap.representation(using: .png, properties: [:])!
try data.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
